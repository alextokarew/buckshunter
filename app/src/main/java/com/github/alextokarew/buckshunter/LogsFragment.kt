package com.github.alextokarew.buckshunter

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.hasKeyWithValueOfType
import com.github.alextokarew.buckshunter.databinding.FragmentLogsBinding


/**
 * A simple [Fragment] subclass.
 * Use the [LogsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        Log.i("LOGS FRAGMENT", "Resume logs")
        val workQuery = WorkQuery.Builder
            .fromTags(listOf(ApiPollWorker.TAG))
            .addStates(listOf(WorkInfo.State.SUCCEEDED))
            .build()

        val workManager = WorkManager.getInstance(requireActivity())
        workManager.getWorkInfosLiveData(workQuery).observe(this, Observer { workInfos ->
            if (_binding != null) {
                val workInfo = workInfos.maxByOrNull { it.outputData.getString("NOW") ?: "0" }
                val data = workInfo!!.outputData
                _binding!!.lastSyncValue.text = data.getString("NOW")
                _binding!!.latValue.text = "%.7f".format(data.getDouble("LAT", 0.0))
                _binding!!.lonValue.text = "%.7f".format(data.getDouble("LON", 0.0))
                _binding!!.closestAtmDistance.text = "%.3f km".format(data.getDouble("CLOSEST_POINT_DISTANCE", 0.0))
                _binding!!.closestAtmAddress.text = data.getString("CLOSEST_POINT_ADDRESS")
                if (data.hasKeyWithValueOfType<Double>("CLOSEST_NEW_POINT_DISTANCE")) {
                    _binding!!.newAtmDistance.text = "%.3f km".format(data.getDouble("CLOSEST_NEW_POINT_DISTANCE", 0.0))
                    _binding!!.newAtmAddress.text = data.getString("CLOSEST_NEW_POINT_ADDRESS")
                }
                _binding!!.logsHolder.text.clear()
                _binding!!.logsHolder.text.append(data.getString(ApiPollWorker.CURRENT_RESULT))
            }
        })
    }
}