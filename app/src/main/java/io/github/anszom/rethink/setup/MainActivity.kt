package io.github.anszom.rethink.setup

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnProvision).setOnClickListener {
            startActivity(Intent(this, ProvisionActivity::class.java))
        }
        findViewById<Button>(R.id.btnDns).setOnClickListener {
            startActivity(Intent(this, DnsCheckActivity::class.java))
        }
    }
}
