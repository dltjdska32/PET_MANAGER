package com.petmanager.presentation.ui.myinfo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.test1.databinding.MyInfoFragBinding
import com.petmanager.presentation.ui.main.MainActivity
import com.example.test1.R


class MyInfoFragment : Fragment() {


    private lateinit var binding: MyInfoFragBinding

    companion object {
        const val TAG : String = "로그"

        fun newInstance() : MyInfoFragment {
            return MyInfoFragment()
        }
    }

    // 메모리에 올라갔을때
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "ChatFragment - onCreate() called")


    }

    // 부모액티비티에 붙었을 때
    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "ChatFragment - onAttach() called")

    }

    //뷰 생성, 프레그먼트와 레이아웃을 연결
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?

    ): View? {
        binding = MyInfoFragBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Reference to the logout button
        val logoutButton: Button = view.findViewById(R.id.logout_btn)
        val editInfoButton: Button = view.findViewById(R.id.editInfo_btn)

        // Set a click listener on the logout button
        logoutButton.setOnClickListener {
            performLogout()
        }

        editInfoButton.setOnClickListener {
            editInfo()
        }
    }

    private fun performLogout() {
        // 액티비티의 인스턴스를 가져와서 함수 호출
        val activity = activity as? MainActivity
        activity?.signOutAndStartSignInActivity()
    }

    private fun editInfo(){
        val activity = activity as? MainActivity
        activity?.editBasicInfo()
    }
}

