package com.hearxgroup.dactest

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_launch.*

class LaunchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)

        btn_dac_v2.setOnClickListener {
            startActivity(
                Intent(this, DACV2Activity::class.java)
            )
        }

        btn_dac_v3_raw.setOnClickListener {
            startActivity(
                Intent(this, DACV3RawActivity::class.java)
            )
        }

        btn_dac_v3.setOnClickListener {

        }

    }
}