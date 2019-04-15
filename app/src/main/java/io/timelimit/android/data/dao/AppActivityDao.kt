package io.timelimit.android.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.timelimit.android.data.model.AppActivity

@Dao
interface AppActivityDao {
    @Query("SELECT * FROM app_activity LIMIT :pageSize OFFSET :offset")
    fun getAppActivityPageSync(offset: Int, pageSize: Int): List<AppActivity>

    @Query("SELECT * FROM app_activity WHERE device_id IN (:deviceIds)")
    fun getAppActivitiesByDeviceIds(deviceIds: List<String>): LiveData<List<AppActivity>>

    @Query("SELECT * FROM app_activity WHERE app_package_name = :packageName")
    fun getAppActivitiesByPackageName(packageName: String): LiveData<List<AppActivity>>

    @Insert
    fun addAppActivitySync(item: AppActivity)

    @Insert
    fun addAppActivitiesSync(items: List<AppActivity>)

    @Query("DELETE FROM app_activity WHERE device_id = :deviceId AND app_package_name = :packageName AND activity_class_name IN (:activities)")
    fun deleteAppActivitiesSync(deviceId: String, packageName: String, activities: List<String>)

    @Query("DELETE FROM app_activity WHERE device_id = :deviceId")
    fun deleteAppActivitiesByDeviceId(deviceId: String)
}