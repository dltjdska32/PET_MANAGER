package com.petmanager.presentation.ui.home

import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test1.databinding.HomeFragBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
import com.petmanager.domain.model.Profiles
import com.petmanager.domain.model.Info
import com.petmanager.domain.model.PostInfo
import com.petmanager.presentation.adapter.HomeAdapter
import com.petmanager.presentation.ui.main.MainActivity
import com.example.test1.R

typealias post_info = PostInfo

class HomeFragment : Fragment() {


    private lateinit var binding: HomeFragBinding
    private lateinit var auth: FirebaseAuth

    companion object {
        const val TAG : String = "로그"


        fun newInstance() : HomeFragment {
            return HomeFragment()
        }
    }

    // 메모리에 올라갔을때
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "HomeFragment - onCreate() called")
    }

    // 부모액티비티에 붙었을 때
    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "HomeFragment - onAttach() called")
    }

    //뷰 생성, 프레그먼트와 레이아웃을 연결
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = HomeFragBinding.inflate(inflater, container, false)


// RecyclerView에 대한 설정
        var profileList = arrayListOf(
            Profiles(R.drawable.walking, "홍대연", "두정동", "5/26~5/27 맡아주실분구해요",false,"1234", "2024년 06월 30일")
        )

        val db = Firebase.firestore
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        var location=""

        db.collection("user")
            .whereEqualTo("userID", currentUser?.uid) //키값넣는거. where절
            .get()
            .addOnSuccessListener { documents ->
                if(documents.isEmpty){
                    val activity = activity as? MainActivity
                    activity?.signOutAndStartSignInActivity()
                } //if문은 임의로 넣은거

                for (document in documents) {
                    val user = document.toObject<Info>()
                    val loc = user?.location
                    Log.d("p", "${loc}")
                    if(loc != null){
                        location = loc
                    }
                    Log.d(ContentValues.TAG, "${document.id} => ${document.data}")
                }

                db.collection("postList")
                    .whereEqualTo("location", location) //키값넣는거. where절
                    .orderBy("deadline")
                    .get()
                    .addOnSuccessListener { documents ->
                        if(documents.isEmpty){
                            profileList = arrayListOf(
                                Profiles(R.drawable.hotel, "없음", "없음", "없음",false,"1234", "없음"),
                            )
                        } //if문은 임의로 넣은거
                        profileList.clear()
                        for (document in documents) {
                            val post = document.toObject<post_info>()
                            val price = post?.price.toString()
                            val title = post?.title.toString()
                            val category = post.management_type.toString()
                            val isFavorite =false;
                            val postID = document.id
                            val deadline = post?.deadline.toString()// deadline 필드 가져오기
                            if(category == "산책"){
                                profileList.add(Profiles(R.drawable.walking,title,category,price,isFavorite,postID, deadline))
                            }else{
                                profileList.add(Profiles(R.drawable.hotel,title,category,price,isFavorite,postID, deadline))
                            }
                            Log.d("postIDIDID", "${postID}")

                            Log.d(ContentValues.TAG, "${document.id} => ${document.data}")
                        }
                        binding.rvProfile.layoutManager = LinearLayoutManager(requireContext())
                        binding.rvProfile.setHasFixedSize(true)
                        binding.rvProfile.adapter = HomeAdapter(profileList)

                    }
                    .addOnFailureListener { exception ->
                        Log.w(ContentValues.TAG, "Error getting documents: ", exception)
                    }

            }
            .addOnFailureListener { exception ->
                Log.w(ContentValues.TAG, "Error getting documents: ", exception)
            }
        Log.d("p", "1${location}")

        return binding.root
    }
}

