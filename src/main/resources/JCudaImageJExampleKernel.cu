extern "C"
__global__ void invert(uchar4* data, int w, int h)
{
    int x = threadIdx.x+blockIdx.x*blockDim.x;
    int y = threadIdx.y+blockIdx.y*blockDim.y;
    if (x < w && y < h)
    {
        int index = y*w+x;
        uchar4 pixel = data[index];
        pixel.x = 255 - pixel.x;
        pixel.y = 255 - pixel.y;
        pixel.z = 255 - pixel.z;
        pixel.w = 255 - pixel.w;
        data[index] = pixel;
    }
}
