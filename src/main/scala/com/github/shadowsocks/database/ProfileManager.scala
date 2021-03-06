/*******************************************************************************/
/*                                                                             */
/*  Copyright (C) 2017 by Max Lv <max.c.lv@gmail.com>                          */
/*  Copyright (C) 2017 by Mygod Studio <contact-shadowsocks-android@mygod.be>  */
/*                                                                             */
/*  This program is free software: you can redistribute it and/or modify       */
/*  it under the terms of the GNU General Public License as published by       */
/*  the Free Software Foundation, either version 3 of the License, or          */
/*  (at your option) any later version.                                        */
/*                                                                             */
/*  This program is distributed in the hope that it will be useful,            */
/*  but WITHOUT ANY WARRANTY; without even the implied warranty of             */
/*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              */
/*  GNU General Public License for more details.                               */
/*                                                                             */
/*  You should have received a copy of the GNU General Public License          */
/*  along with this program. If not, see <http://www.gnu.org/licenses/>.       */
/*                                                                             */
/*******************************************************************************/

package com.github.shadowsocks.database

import android.util.Log
import com.github.shadowsocks.ProfilesFragment
import com.github.shadowsocks.ShadowsocksApplication.app
import com.rallets.RUtils

object ProfileManager {
  private final val TAG = "ProfileManager"
}

class ProfileManager(dbHelper: DBHelper) {
  import ProfileManager._

  def createOrUpdateProfile(profile: Profile = null): Profile = {
    if (profile == null) return null
    profile.id = 0
    try {
      app.currentProfile match {
        case Some(oldProfile) =>
          // Copy Feature Settings from old profile
          profile.route = oldProfile.route
          profile.ipv6 = oldProfile.ipv6
          profile.proxyApps = oldProfile.proxyApps
          profile.bypass = oldProfile.bypass
          profile.individual = oldProfile.individual
          profile.udpdns = oldProfile.udpdns
        case _ =>
      }
      val dao = dbHelper.profileDao
      val last = dao.queryRaw(dao.queryBuilder.selectRaw("MAX(userOrder)").prepareStatementString).getFirstResult
      if (last != null && last.length == 1 && last(0) != null) profile.userOrder = last(0).toInt + 1
      val sameProfiles = dao.queryForEq("_id", profile._id)
      if (sameProfiles.isEmpty) {
        dao.create(profile)
        if (ProfilesFragment.instance != null) ProfilesFragment.instance.profilesAdapter.add(profile)
      } else {
        val updateBuilder = dao.updateBuilder()
        updateBuilder.where().eq("_id", profile._id)
        updateBuilder.updateColumnValue("localPort", profile.localPort)
          .updateColumnValue("remotePort", profile.remotePort)
          .updateColumnValue("host", profile.host)
          .updateColumnValue("name", profile.name)
          .updateColumnValue("password", profile.password)
          .updateColumnValue("kcp", profile.kcp)
          .updateColumnValue("kcpPort", profile.kcpPort)
          .updateColumnValue("kcpcli", profile.kcpcli).update()
        ProfilesFragment.instance.profilesAdapter.deepRefreshId(sameProfiles.get(0).id)
      }
    } catch {
      case ex: Exception =>
        Log.e(TAG, "addProfile", ex)
        app.track(ex)
    }
    profile
  }

  def updateProfile(profile: Profile): Boolean = {
    try {
      dbHelper.profileDao.update(profile)
      true
    } catch {
      case ex: Exception =>
        Log.e(TAG, "updateProfile", ex)
        app.track(ex)
        false
    }
  }

  def getProfile(id: Int): Option[Profile] = {
    try {
      dbHelper.profileDao.queryForId(id) match {
        case profile: Profile => Option(profile)
        case _ => None
      }
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getProfile", ex)
        app.track(ex)
        None
    }
  }

  def delProfile(id: Int): Boolean = {
    try {
      dbHelper.profileDao.deleteById(id)
      if (ProfilesFragment.instance != null) ProfilesFragment.instance.profilesAdapter.removeId(id)
      true
    } catch {
      case ex: Exception =>
        Log.e(TAG, "delProfile", ex)
        app.track(ex)
        false
    }
  }

  def delAllProfile: Boolean = {
    try {
      dbHelper.profileDao.queryRaw("delete from profile")
      dbHelper.profileDao.queryRaw("update sqlite_sequence SET seq = 0 where name ='profile'")
      true
    } catch {
      case ex: Exception =>
        Log.e(TAG, "delAllProfile", ex)
        false
    }
  }

  def getFirstProfile: Option[Profile] = {
    try {
      val result = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.limit(1L).prepare)
      if (result.size == 1) Option(result.get(0)) else None
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getAllProfiles", ex)
        app.track(ex)
        None
    }
  }

  def getAllProfiles: Option[List[Profile]] = {
    try {
      import scala.collection.JavaConversions._
      Option(dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder.orderBy("userOrder", true).prepare).toList)
    } catch {
      case ex: Exception =>
        Log.e(TAG, "getAllProfiles", ex)
        app.track(ex)
        None
    }
  }
}
