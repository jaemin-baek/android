package com.jaeminbaek.multiplesurfaces

import android.graphics.SurfaceTexture
import android.net.Uri
import android.opengl.GLES20
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerView
import com.jaeminbaek.multiplesurfaces.gles.EglCore
import com.jaeminbaek.multiplesurfaces.gles.FullFrameRect
import com.jaeminbaek.multiplesurfaces.gles.Texture2dProgram
import com.jaeminbaek.multiplesurfaces.gles.WindowSurface

class MainActivity : AppCompatActivity(), SurfaceTexture.OnFrameAvailableListener {

    private lateinit var playerView: PlayerView
    private lateinit var surfaceView: SurfaceView

    private lateinit var player: SimpleExoPlayer

    private var eglCore: EglCore? = null
    private var fullFrameBlit: FullFrameRect? = null
    private var textureId: Int = 0
    private var videoSurfaceTexture: SurfaceTexture? = null
    private val transformMatrix = FloatArray(16)

    private var mainDisplaySurface: WindowSurface? = null
    private var secondaryDisplaySurface: WindowSurface? = null

    private var surface: Surface? = null

    private val surfaceViewHolderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            secondaryDisplaySurface = WindowSurface(eglCore, holder.surface, false)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
        override fun surfaceDestroyed(holder: SurfaceHolder) {}
    }

    private val playerViewHolderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            eglCore = EglCore()

            mainDisplaySurface = WindowSurface(eglCore, holder.surface, false).apply {
                makeCurrent()
            }
            fullFrameBlit = FullFrameRect(Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT))
            textureId = fullFrameBlit!!.createTextureObject()
            videoSurfaceTexture = SurfaceTexture(textureId).also {
                it.setOnFrameAvailableListener(this@MainActivity)
            }

            surface = Surface(videoSurfaceTexture)

            player.setVideoSurface(surface)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
        override fun surfaceDestroyed(holder: SurfaceHolder) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        surfaceView = findViewById(R.id.surfaceView)

        player = SimpleExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri("asset:///test.mp4"))
            playWhenReady = true
        }

        playerView.player = player

        (playerView.videoSurfaceView as SurfaceView).holder.addCallback(playerViewHolderCallback)
        surfaceView.holder.addCallback(surfaceViewHolderCallback)

        player.prepare()
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        if (eglCore == null) return

        // PlayerView
        mainDisplaySurface?.let {
            drawFrame(it, playerView.width, playerView.height)
        }

        // SurfaceView
        secondaryDisplaySurface?.let {
            drawFrame(it, surfaceView.width, surfaceView.height)
        }
    }

    private fun drawFrame(windowSurface: WindowSurface, viewWidth: Int, viewHeight: Int) {
        windowSurface.makeCurrent()

        videoSurfaceTexture!!.apply {
            updateTexImage()
            getTransformMatrix(transformMatrix)
        }

        GLES20.glViewport(0, 0, viewWidth, viewHeight)

        fullFrameBlit!!.drawFrame(textureId, transformMatrix)

        windowSurface.swapBuffers()
    }

    override fun onPause() {
        surface?.release()
        surface = null

        videoSurfaceTexture?.release()
        videoSurfaceTexture = null

        mainDisplaySurface?.release()
        mainDisplaySurface = null

        secondaryDisplaySurface?.release()
        secondaryDisplaySurface = null

        fullFrameBlit?.release(false)
        fullFrameBlit = null

        eglCore?.release()
        eglCore = null

        super.onPause()
    }
}