/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.objectdetection.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mediapipe.examples.objectdetection.MainViewModel
import com.google.mediapipe.examples.objectdetection.R
import com.google.mediapipe.examples.objectdetection.AudioClassifierHelper
import com.google.mediapipe.examples.objectdetection.databinding.FragmentCameraBinding
import com.google.mediapipe.examples.objectdetection.databinding.FragmentRecorderBinding
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.Category
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class RecorderFragment : Fragment(), AudioClassifierHelper.ClassifierListener {
    private var _fragmentBinding: FragmentRecorderBinding? = null
    private val fragmentRecorderBinding get() = _fragmentBinding!!
    private lateinit var audioClassifierHelper: AudioClassifierHelper
    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var backgroundExecutor: ExecutorService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentBinding =
            FragmentRecorderBinding.inflate(inflater, container, false)
        return fragmentRecorderBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.e("Amber", "onViewCreated AUDIO")
        super.onViewCreated(view, savedInstanceState)
        backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor.execute {
            audioClassifierHelper =
                AudioClassifierHelper(
                    context = requireContext(),
                    classificationThreshold = viewModel.currentThreshold,
                    overlap = viewModel.currentOverlapPosition,
                    numOfResults = viewModel.currentMaxResults,
                    runningMode = RunningMode.AUDIO_STREAM,
                    listener = this
                )
        }
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(),
                R.id.fragment_container
            )
                .navigate(R.id.action_permissions_to_camera)
        }
        backgroundExecutor.execute {
            if (audioClassifierHelper.isClosed()) {
                audioClassifierHelper.initClassifier()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.e("Amber Audio", "onPause in Recorder")
        while (!::audioClassifierHelper.isInitialized) {
        }
        // save audio classifier settings
        viewModel.apply {
            setThreshold(audioClassifierHelper.classificationThreshold)
            setMaxResults(audioClassifierHelper.numOfResults)
            setOverlap(audioClassifierHelper.overlap)
        }

        backgroundExecutor.execute {
            if (::audioClassifierHelper.isInitialized) {
                audioClassifierHelper.stopAudioClassification()
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS

        )
    }

    override fun onError(error: String) {
    }

    override fun onResult(resultBundle: AudioClassifierHelper.ResultBundle) {
        resultBundle.results[0].classificationResults().first().classifications()?.get(0)?.categories()?.let {
            Log.e("!!!!!!!", it.toString())
            for (item in it) {
                if(item.categoryName()=="Typing" || item.categoryName()== "Speech") {
                    lifecycleScope.launchWhenStarted {
                        Navigation.findNavController(
                            requireActivity(),
                            R.id.fragment_container
                        )
                            .navigate(R.id.action_audio_fragment_to_camera_fragment)
                    }
                }
            }
        }

        activity?.runOnUiThread {
//            if (_fragmentBinding != null) {
//                resultBundle.results[0].classificationResults().first()
//                    .classifications()?.get(0)?.categories()?.let {
//                        // Show result on bottom sheet
//                        probabilitiesAdapter.updateCategoryList(it)
//                    }
//            }
        }
    }
}
