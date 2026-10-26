package android.media

import android.graphics.ImageFormat

/** Image's SDK constructor is package-private; this host-only fixture supplies no native pixels. */
internal class SyntheticMediaImage : Image() {
    override fun getFormat() = ImageFormat.YUV_420_888
    override fun getWidth() = 400
    override fun getHeight() = 200
    override fun getTimestamp() = 42L
    override fun setTimestamp(timestamp: Long) = Unit
    override fun getPlanes(): Array<Plane> = emptyArray()
    override fun close() = Unit
}
