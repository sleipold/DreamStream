package com.sleipold.dreamstream

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import com.google.zxing.WriterException
import android.R.attr.bitmap
import android.graphics.Point
import android.view.WindowManager
import android.widget.ImageView


class ReceiverConnection : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiver_connection)

        var inputValue = "foobar42"
        if (inputValue.isNotEmpty()) {
            var qrImage: ImageView = findViewById(R.id.ivQrImage)
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val point = Point()
            display.getSize(point)
            val width = point.x
            val height = point.y
            var smallerDimension = if (width < height) width else height
            smallerDimension = smallerDimension * 3 / 4

            var qrgEncoder = QRGEncoder(
                inputValue, null,
                QRGContents.Type.TEXT,
                smallerDimension
            )
            try {
                var bitmap = qrgEncoder.encodeAsBitmap()
                qrImage.setImageBitmap(bitmap)
            } catch (e: WriterException) {
                println(e.toString())
            }

        } else {
            println("inputValue must not be empty")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Adding menu to activity
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // onClick listener for menu
        when(item.itemId) {
            R.id.home -> {
                // home icon got clicked -> open welcome activity
                val homeIntent = Intent(this, Welcome::class.java)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // TODO: reset running connection
                startActivity(homeIntent)
            }

            R.id.settings -> {
                // gear icon got clicked -> open settings activity
                val intent = Intent(this, Settings::class.java)
                startActivity(intent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
