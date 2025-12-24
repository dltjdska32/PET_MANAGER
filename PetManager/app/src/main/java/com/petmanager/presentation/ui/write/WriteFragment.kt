package com.petmanager.presentation.ui.write

import android.animation.AnimatorInflater
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.example.test1.databinding.HomeFragBinding
import com.example.test1.databinding.WriteFragBinding
import com.google.firebase.firestore.toObject
import java.util.Calendar
import android.app.DatePickerDialog
import com.petmanager.presentation.ui.home.HomeFragment
import com.petmanager.presentation.ui.main.MainActivity
import com.petmanager.domain.model.Info
import com.petmanager.domain.model.PostInfo
import com.example.test1.R

typealias post_info = PostInfo

class WriteFragment : Fragment() {

    private lateinit var binding: WriteFragBinding
    private lateinit var auth: FirebaseAuth

    private var selectedDate: String? = null // 여기서 selectedDate 변수를 선언 및 초기화

    companion object {
        const val TAG: String = "로그"

        fun newInstance(): WriteFragment {
            return WriteFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "WriteFragment - onCreate() called")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "WriteFragment - onAttach() called")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = WriteFragBinding.inflate(inflater, container, false)

        val db = Firebase.firestore
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        val date_btn: Button = binding.dateBtn
        val cancel_btn: Button = binding.cancelBtn
        val post_btn: Button = binding.postBtn

        var management_type: String? = null
        var animal_type: String? = null

        var check_manage_type: Boolean = false
        var management_type_group = binding.toggleButton1

        var check_animal_type: Boolean = false
        var animal_type_group = binding.toggleButton2

        cancel_btn.setOnClickListener() {
            val homeFragment = HomeFragment()
            val transaction = parentFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, homeFragment)
            transaction.addToBackStack(null)
            transaction.commit()
        }

        management_type_group.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.category1_hotel_btn -> management_type = "케어"
                    R.id.category1_walk_btn -> management_type = "산책"
                }
            } else {
                if (management_type_group.checkedButtonId == View.NO_ID) {
                    check_manage_type = true
                }
            }
        }

        animal_type_group.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.category2_cat_btn -> animal_type = "고양이"
                    R.id.category2_dog_btn -> animal_type = "강아지"
                }
            } else {
                check_animal_type = true
            }
        }

        post_btn.setOnClickListener() {
            var title = binding.titleTxt.text.toString()
            var price = binding.priceTxt.text.toString()
            var contents = binding.contentsEdt.text.toString()

            if (title.isEmpty() || price.isEmpty() || management_type == null ||
                animal_type == null || contents.isEmpty() || selectedDate == null
            ) {
                Toast.makeText(activity, "정보를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var intPrice = price.toInt()

            db.collection("user")
                .whereEqualTo("userID", currentUser?.uid)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        val activity = activity as? MainActivity
                        activity?.signOutAndStartSignInActivity()
                    }

                    for (document in documents) {
                        val user = document.toObject<Info>()
                        val loc = user?.location
                        if (loc != null) {
                            val postInfo = PostInfo(
                                currentUser?.uid,
                                title,
                                intPrice,
                                management_type,
                                animal_type,
                                contents,
                                loc,
                                selectedDate
                            )
                            db.collection("postList")
                                .add(postInfo)
                                .addOnSuccessListener { documentReference ->
                                    Log.d(
                                        ContentValues.TAG,
                                        "DocumentSnapshot written with ID: ${documentReference.id}"
                                    )
                                }
                                .addOnFailureListener { e ->
                                    Log.w(ContentValues.TAG, "Error adding document", e)
                                }
                        }

                        Log.d(ContentValues.TAG, "${document.id} => ${document.data}")
                    }

                }
                .addOnFailureListener { exception ->
                    Log.w(ContentValues.TAG, "Error getting documents: ", exception)
                }

            val homeFragment = HomeFragment()
            val transaction = parentFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, homeFragment)
            transaction.addToBackStack(null)
            transaction.commit()
        }

        date_btn.setOnClickListener {
            showDatePickerDialog()
        }

        return binding.root
    }

    fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val dpd = DatePickerDialog(
            requireContext(),
            { view, year, monthOfYear, dayOfMonth ->
                selectedDate = "$year-${monthOfYear + 1}-$dayOfMonth"
                binding.dateBtn.text = selectedDate // 버튼 텍스트 업데이트
            },
            year,
            month,
            day
        )

        dpd.show()
    }
}

