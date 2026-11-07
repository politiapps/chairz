/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.util

import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.SystemUiController.FLAG_DARK_NAV
import com.android.launcher3.util.SystemUiController.FLAG_DARK_STATUS
import com.android.launcher3.util.SystemUiController.FLAG_LIGHT_NAV
import com.android.launcher3.util.SystemUiController.FLAG_LIGHT_STATUS
import com.android.launcher3.util.SystemUiController.UI_STATE_BASE_WINDOW
import com.android.launcher3.util.SystemUiController.UI_STATE_SCRIM_VIEW
import com.android.launcher3.util.SystemUiController.UI_STATE_SHORTCUT_TRAY
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class SystemUiControllerTest {

    @Mock private lateinit var window: Window
    @Mock private lateinit var decorView: View

    private lateinit var underTest: SystemUiController

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        `when`(window.decorView).thenReturn(decorView)
        underTest = SystemUiController(window.decorView)
    }

    @Test
    fun test_default_state() {
        assertThat(underTest.toString()).isEqualTo("mStates=[0, 0, 0, 0, 0, 0]")
    }

    @Test
    fun update_state_base_window_light() {
        underTest.updateUiState(UI_STATE_BASE_WINDOW, /* isLight= */ true)

        val visibility =
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        verify(decorView).systemUiVisibility = eq(visibility)
        assertThat(underTest.baseSysuiVisibility).isEqualTo(visibility)
        val flag = FLAG_LIGHT_NAV or FLAG_LIGHT_STATUS
        assertThat(underTest.toString()).isEqualTo("mStates=[$flag, 0, 0, 0, 0, 0]")
    }

    @Test
    fun update_state_scrim_view_light() {
        underTest.updateUiState(UI_STATE_SCRIM_VIEW, /* isLight= */ true)

        verify(decorView).systemUiVisibility =
            eq(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        assertThat(underTest.baseSysuiVisibility).isEqualTo(0)
        val flag = FLAG_LIGHT_NAV or FLAG_LIGHT_STATUS
        assertThat(underTest.toString()).isEqualTo("mStates=[0, $flag, 0, 0, 0, 0]")
    }

    @Test
    fun update_state_base_window_dark() {
        underTest.updateUiState(UI_STATE_BASE_WINDOW, /* isLight= */ false)

        verify(decorView, never()).systemUiVisibility = anyInt()
        assertThat(underTest.baseSysuiVisibility).isEqualTo(0)
        val flag = FLAG_DARK_NAV or FLAG_DARK_STATUS
        assertThat(underTest.toString()).isEqualTo("mStates=[$flag, 0, 0, 0, 0, 0]")
    }

    @Test
    fun update_state_scrim_view_dark() {
        underTest.updateUiState(UI_STATE_SCRIM_VIEW, /* isLight= */ false)

        verify(decorView, never()).systemUiVisibility = anyInt()
        assertThat(underTest.baseSysuiVisibility).isEqualTo(0)
        val flag = FLAG_DARK_NAV or FLAG_DARK_STATUS
        assertThat(underTest.toString()).isEqualTo("mStates=[0, $flag, 0, 0, 0, 0]")
    }

    @Test
    fun get_base_sysui_visibility() {
        `when`(decorView.systemUiVisibility).thenReturn(SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        assertThat(underTest.baseSysuiVisibility).isEqualTo(SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    @Test
    fun update_state_base_window_light_with_existing_visibility() {
        `when`(decorView.systemUiVisibility).thenReturn(SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        underTest.updateUiState(UI_STATE_BASE_WINDOW, /* isLight= */ true)

        val visibility =
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        assertThat(underTest.baseSysuiVisibility).isEqualTo(visibility)
        verify(decorView).systemUiVisibility = eq(visibility)
    }

    @Test
    fun shortcut_tray_changes_status_icons_without_changing_navigation() {
        val initial = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        `when`(decorView.systemUiVisibility).thenReturn(initial)
        underTest.updateUiState(UI_STATE_BASE_WINDOW, FLAG_DARK_STATUS or FLAG_LIGHT_NAV)

        underTest.updateUiState(UI_STATE_SHORTCUT_TRAY, FLAG_LIGHT_STATUS)

        verify(decorView).systemUiVisibility = eq(initial or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
    }

    @Test
    fun closing_shortcut_tray_restores_underlying_status_icons() {
        underTest.updateUiState(UI_STATE_BASE_WINDOW, FLAG_DARK_STATUS or FLAG_DARK_NAV)
        underTest.updateUiState(UI_STATE_SHORTCUT_TRAY, FLAG_LIGHT_STATUS)
        `when`(decorView.systemUiVisibility).thenReturn(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        clearInvocations(decorView)

        underTest.updateUiState(UI_STATE_SHORTCUT_TRAY, 0)

        verify(decorView).systemUiVisibility = eq(0)
    }

    @Test
    fun shortcut_tray_overrides_scrim_until_removed() {
        underTest.updateUiState(UI_STATE_SHORTCUT_TRAY, FLAG_LIGHT_STATUS)
        `when`(decorView.systemUiVisibility).thenReturn(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        clearInvocations(decorView)

        underTest.updateUiState(UI_STATE_SCRIM_VIEW, FLAG_DARK_STATUS)
        verify(decorView, never()).systemUiVisibility = anyInt()

        underTest.updateUiState(UI_STATE_SHORTCUT_TRAY, 0)
        verify(decorView).systemUiVisibility = eq(0)
    }
}
