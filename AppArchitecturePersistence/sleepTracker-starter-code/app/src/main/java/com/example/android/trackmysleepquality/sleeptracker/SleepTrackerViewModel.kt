/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import android.provider.SyncStateContract.Helpers.insert
import android.provider.SyncStateContract.Helpers.update
import androidx.lifecycle.*
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

        //Define viewModelJob and assign it an instance of Job
        private var viewModelJob = Job()

        //Override onCleared() and cancel all coroutines.
        override fun onCleared() {
                super.onCleared()
                viewModelJob.cancel()
        }

        //Define a uiScope for the coroutines:
        private val uiScope = CoroutineScope(Dispatchers.Main +  viewModelJob)

        //Define a variable, tonight, to hold the current night, and make it MutableLiveData
        private var tonight = MutableLiveData<SleepNight?>()

        //Create variable nights. Then getAllNights() from the database and assign to the nights variable
        private val nights = database.getAllNights()

        //Add code to transform nights into a nightsString using the formatNights()
        //function from Util.kt
        val nightsString = Transformations.map(nights) { nights ->
                formatNights(nights, application.resources)
        }

        //In SleepTrackerViewModel.kt, in onStopTracking() set a LiveData that changes
        //when you want to navigate.
        private val _navigateToSleepQuality = MutableLiveData<SleepNight>()

        val navigateToSleepQuality: LiveData<SleepNight>
                get() = _navigateToSleepQuality

        //Add a doneNavigating() function that resets the event
        fun doneNavigating() {
                _navigateToSleepQuality.value = null
        }

        //The START button should be visible when tonight is null, the STOP
        //button when tonight is not null, and the CLEAR button if nights contains any nights:
        val startButtonVisible = Transformations.map(tonight) {
                null == it
        }
        val stopButtonVisible = Transformations.map(tonight) {
                null != it
        }
        val clearButtonVisible = Transformations.map(nights) {
                it?.isNotEmpty()
        }

        //Snackbar - create the encapsulated event
        private var _showSnackbarEvent = MutableLiveData<Boolean>()

        val showSnackBarEvent: LiveData<Boolean>
                get() = _showSnackbarEvent

        //implement doneShowingSnackbar()
        fun doneShowingSnackbar() {
                _showSnackbarEvent.value = false
        }

        //To initialize the tonight variable, create an init block and call initializeTonight()
        init {
                initializeTonight()
        }

        //Implement initializeTonight(). In the uiScope, launch a coroutine.
        //    Inside, get the value for tonight from the database by calling getTonightFromDatabase(),
        //    which you will define in the next step, and assign it to tonight.value
        private fun initializeTonight() {
                uiScope.launch {
                        tonight.value = getTonightFromDatabase()
                }
        }

        //Implement getTonightFromDatabase(). Define is as a private suspend function that
        //returns a nullable SleepNight, if there is no current started sleepNight.
        //
        //Let the coroutine get tonight from the database. If the start and end times are the not
        // the same, meaning, the night has already been completed, return null.
        // Otherwise, return night
        private suspend fun getTonightFromDatabase():  SleepNight? {
                return withContext(Dispatchers.IO) {
                        var night = database.getTonight()

                        if (night?.endTimeMilli != night?.startTimeMilli) {
                                night = null
                        }
                        night
                }
        }

        //Implement onStartTracking(), the click handler for the Start button
        fun onStartTracking() {
                uiScope.launch {
                        val newNight = SleepNight()
                        insert(newNight)
                        tonight.value = getTonightFromDatabase()
                }
        }

        //Define insert() as a private suspend function that takes a SleepNight as its argument
        private suspend fun insert(night: SleepNight){
                withContext(Dispatchers.IO) {
                        database.insert(night)
                }
        }

        private suspend fun update(night: SleepNight) {
                withContext(Dispatchers.IO) {
                        database.update(night)
                }
        }

        //Add onStopTracking() to the view model. Launch a coroutine in the uiScope
        fun onStopTracking() {
                uiScope.launch {
                        val oldNight = tonight.value ?: return@launch
                        _navigateToSleepQuality.value = oldNight
                        oldNight.endTimeMilli = System.currentTimeMillis()
                        update(oldNight)
                }
        }

        fun onClear() {
                uiScope.launch {
                        clear()
                        tonight.value = null
                        _showSnackbarEvent.value = true
                }
        }

        suspend fun clear() {
                withContext(Dispatchers.IO) {
                        database.clear()
                }
        }


}

