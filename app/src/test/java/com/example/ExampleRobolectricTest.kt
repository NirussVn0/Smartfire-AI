package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SmartFireDatabase
import com.example.data.local.entity.HomeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun testAppNameString() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SmartFire AI", appName)
    }

    @Test
    fun testDatabaseInitialization() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = SmartFireDatabase.getInstance(context)
        val dao = db.smartFireDao()

        dao.insertHome(
            HomeEntity(
                id = "test_home_1",
                name = "Trường THPT 12D3",
                buildingType = "Trường học"
            )
        )

        val homes = dao.getAllHomes().first()
        assertNotNull(homes)
        assertEquals(1, homes.count { it.id == "test_home_1" })
    }
}
