package com.tea.aim

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class DetectorEngine(private val context: Context) {

    private val inputSize = 640
    private var confThreshold = 0.45f
    private var iouThreshold  = 0.45f

    private val interpreter: Interpreter

    init {
        val opts = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(loadModel("yolov8n_int8.tflite"), opts)
    }

    fun setThresholds(conf: Float, iou: Float) {
        confThreshold = conf; iouThreshold = iou
    }

    private fun loadModel(name: String): ByteBuffer {
        val fd = context.assets.openFd(name)
        FileInputStream(fd.fileDescriptor).use { fis ->
            return fis.channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset, fd.declaredLength
            )
        }
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val input   = preprocess(resized)
        val output  = Array(1) { Array(84) { FloatArray(8400) } }
        interpreter.run(input, output)
        return postprocess(output[0], bitmap.width, bitmap.height)
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
        buf.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            buf.putFloat(((p shr 16) and 0xFF) / 255f)
            buf.putFloat(((p shr 8)  and 0xFF) / 255f)
            buf.putFloat((p and 0xFF)         / 255f)
        }
        buf.rewind()
        return buf
    }

    private fun postprocess(out: Array<FloatArray>, w: Int, h: Int): List<Detection> {
        val raw = ArrayList<Detection>(200)
        val n = out[0].size
        for (i in 0 until n) {
            val cx = out[0][i]; val cy = out[1][i]
            val bw = out[2][i]; val bh = out[3][i]
            var best = 0f; var cls = -1
            for (c in 4 until out.size) {
                if (out[c][i] > best) { best = out[c][i]; cls = c - 4 }
            }
            if (best < confThreshold) continue
            val x1 = (cx - bw / 2f) * w / inputSize
            val y1 = (cy - bh / 2f) * h / inputSize
            val x2 = (cx + bw / 2f) * w / inputSize
            val y2 = (cy + bh / 2f) * h / inputSize
            raw += Detection(
                max(0f, x1), max(0f, y1),
                min(w.toFloat(), x2), min(h.toFloat(), y2),
                best, cls.toString()
            )
        }
        return nms(raw)
    }

    private fun nms(list: List<Detection>): List<Detection> {
        val s = list.sortedByDescending { it.confidence }.toMutableList()
        val keep = ArrayList<Detection>()
        while (s.isNotEmpty()) {
            val a = s.removeAt(0); keep += a
            s.removeAll { b -> iou(a, b) > iouThreshold }
        }
        return keep
    }

    private fun iou(a: Detection, b: Detection): Float {
        val x1 = max(a.left, b.left); val y1 = max(a.top, b.top)
        val x2 = min(a.right, b.right); val y2 = min(a.bottom, b.bottom)
        val i = max(0f, x2 - x1) * max(0f, y2 - y1)
        val u = a.area + b.area - i
        return if (u <= 0f) 0f else i / u
    }

    fun close() {
        interpreter.close()
    }
}