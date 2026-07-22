package net.phoenixvine.solaris.client.render;

public class MapViewport {

    private static final float ZOOM_STEP = 0.1f;

    private float zoomMin;
    private float zoomMax;

    private double offsetX;
    private double offsetY;
    private float zoom = 1.0f;

    public MapViewport() {
        this(0.25f, 4.0f);
    }

    public MapViewport(float zoomMin, float zoomMax) {
        this.zoomMin = zoomMin;
        this.zoomMax = zoomMax;
    }

    public void pan(double dx, double dy) {
        this.offsetX += dx;
        this.offsetY += dy;
    }

    public boolean adjustZoomToAnchor(double scrollDelta, double mouseX, double mouseY, int originX, int originY) {
        float oldZoom = this.zoom;
        this.zoom = Math.max(zoomMin, Math.min(zoomMax, this.zoom + (float) scrollDelta * ZOOM_STEP));
        if (this.zoom == oldZoom) return false;

        float ratio = this.zoom / oldZoom;
        double localX = mouseX - originX;
        double localY = mouseY - originY;
        this.offsetX = localX - (localX - this.offsetX) * ratio;
        this.offsetY = localY - (localY - this.offsetY) * ratio;
        return true;
    }

    public double toWorldX(double screenX, int originX) {
        return (screenX - originX - offsetX) / zoom;
    }

    public double toWorldZ(double screenY, int originY) {
        return (screenY - originY - offsetY) / zoom;
    }

    public double toScreenX(double worldX, int originX) {
        return worldX * zoom + originX + offsetX;
    }

    public double toScreenY(double worldZ, int originY) {
        return worldZ * zoom + originY + offsetY;
    }

    public void setOffset(double x, double y) {
        this.offsetX = x;
        this.offsetY = y;
    }

    public void raiseZoomMin(float newZoomMin) {
        if (newZoomMin > zoomMin) zoomMin = newZoomMin;
        if (zoom < zoomMin) zoom = zoomMin;
        if (zoomMin > zoomMax) zoomMax = zoomMin * 2f;
    }

    public void clampOffsetToCover(double contentSize, int frameX, int frameW, int frameY, int frameH) {
        double renderedW = contentSize * zoom;
        double minX = frameX + frameW - renderedW;
        double maxX = frameX;
        offsetX = minX > maxX ? (minX + maxX) / 2.0 : clampD(offsetX, minX, maxX);

        double renderedH = contentSize * zoom;
        double minY = frameY + frameH - renderedH;
        double maxY = frameY;
        offsetY = minY > maxY ? (minY + maxY) / 2.0 : clampD(offsetY, minY, maxY);
    }

    private static double clampD(double v, double min, double max) {
        return Math.min(Math.max(v, min), max);
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public float getZoom() {
        return zoom;
    }
}
