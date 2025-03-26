package com.flyjingfish.fast_transform

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.flyjingfish.transform_plugin.databinding.ActivityMainBinding

class MainActivity: AppCompatActivity(){
    //    val haha = 1
    lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }


}