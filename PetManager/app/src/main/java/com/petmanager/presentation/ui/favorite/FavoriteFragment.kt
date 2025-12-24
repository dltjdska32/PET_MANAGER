package com.petmanager.presentation.ui.favorite

import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test1.databinding.FavoriteFragBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
import com.petmanager.domain.model.Profiles
import com.petmanager.domain.model.Info
import com.petmanager.domain.model.PostInfo
import com.petmanager.domain.model.FavoritePost
import com.petmanager.presentation.adapter.ProfileAdapter
import com.example.test1.R

typealias post_info = PostInfo

class FavoriteFragment : Fragment() {

    private lateinit var binding: FavoriteFragBinding
    private lateinit var auth: FirebaseAuth

    companion object {
        const val TAG: String = "로그"

        fun newInstance(): FavoriteFragment {
            return FavoriteFragment()
        }
    }

    // 메모리에 올라갔을때
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "FavoriteFragment - onCreate() called")
    }

    // 부모액티비티에 붙었을 때
    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "Favorite - onAttach() called")
    }

    //뷰 생성, 프레그먼트와 레이아웃을 연결
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FavoriteFragBinding.inflate(inflater, container, false)


        // RecyclerView에 대한 설정
        var profileList = arrayListOf(
            Profiles(R.drawable.walking, "홍대연", "두정동", "5/26~5/27 맡아주실분구해요", false, "1234", "2024년 6월 30일")
        )

        val db = Firebase.firestore
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        var userID = ""

        db.collection("user")
            .whereEqualTo("userID", currentUser?.uid) //키값넣는거. where절
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val user = document.toObject<Info>()
                    val id = user?.userID
                    Log.d("p", "${id}")
                    if (id != null) {
                        userID = id
                    }
                    Log.d(ContentValues.TAG, "${document.id} => ${document.data}")
                }


                db.collection("Favorite")
                    .whereEqualTo("userID", userID)
                    .get()
                    .addOnSuccessListener { documents ->
                        for (document in documents) {
                            val post = document.toObject<FavoritePost>()
                            val id = post?.postID
                            if (id != null) {
                                profileList.clear()
                                db.collection("postList")
                                    .document(id) //키값넣는거. where절
                                    .get()
                                    .addOnSuccessListener { document ->
                                        val post = document.toObject<post_info>()
                                        val price = post?.price.toString()
                                        val title = post?.title.toString()
                                        val category = post?.management_type.toString()
                                        val isFavorite = false;
                                        val postID = document.id
                                        val deadline = post?.deadline.toString()
                                        Log.d("postIDIDID", "${postID}")
                                        if(category =="산책"){
                                            profileList.add(
                                                Profiles(
                                                    R.drawable.walking,
                                                    title,
                                                    category,
                                                    price,
                                                    isFavorite,
                                                    postID,
                                                    deadline
                                                )
                                            )
                                        }else{
                                            profileList.add(
                                                Profiles(
                                                    R.drawable.hotel,
                                                    title,
                                                    category,
                                                    price,
                                                    isFavorite,
                                                    postID,
                                                    deadline
                                                )
                                            )
                                        }

                                        if(profileList != null){
                                            binding.rvProfile.layoutManager = LinearLayoutManager(requireContext())
                                            binding.rvProfile.setHasFixedSize(true)
                                            binding.rvProfile.adapter = ProfileAdapter(profileList)
                                        }
                                        Log.d(
                                            ContentValues.TAG,
                                            "${document.id} => ${document.data}"
                                        )
                                    }
                            }
                        }



                    }
            }
            .addOnFailureListener { exception ->
                Log.w(ContentValues.TAG, "Error getting documents: ", exception)
            }






        return binding!!.root
    }



}

