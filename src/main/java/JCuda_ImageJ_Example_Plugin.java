/**
 * ImageJ Plugin using JCuda
 * 
 * Copyright (c) 2013-2018 Marco Hutter - http://www.jcuda.org
 */

import static jcuda.driver.JCudaDriver.cuCtxCreate;
import static jcuda.driver.JCudaDriver.cuCtxSynchronize;
import static jcuda.driver.JCudaDriver.cuDeviceGet;
import static jcuda.driver.JCudaDriver.cuInit;
import static jcuda.driver.JCudaDriver.cuLaunchKernel;
import static jcuda.driver.JCudaDriver.cuMemAlloc;
import static jcuda.driver.JCudaDriver.cuMemFree;
import static jcuda.driver.JCudaDriver.cuMemcpyDtoH;
import static jcuda.driver.JCudaDriver.cuMemcpyHtoD;
import static jcuda.driver.JCudaDriver.cuModuleGetFunction;
import static jcuda.driver.JCudaDriver.cuModuleLoadData;
import static jcuda.nvrtc.JNvrtc.nvrtcCompileProgram;
import static jcuda.nvrtc.JNvrtc.nvrtcCreateProgram;
import static jcuda.nvrtc.JNvrtc.nvrtcDestroyProgram;
import static jcuda.nvrtc.JNvrtc.nvrtcGetPTX;
import static jcuda.nvrtc.JNvrtc.nvrtcGetProgramLog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.driver.CUcontext;
import jcuda.driver.CUdevice;
import jcuda.driver.CUdeviceptr;
import jcuda.driver.CUfunction;
import jcuda.driver.CUmodule;
import jcuda.driver.JCudaDriver;
import jcuda.nvrtc.JNvrtc;
import jcuda.nvrtc.nvrtcProgram;


/**
 * A simple example for an ImageJ Plugin that uses JCuda.
 */
public class JCuda_ImageJ_Example_Plugin implements PlugInFilter
{
	/**
	 * The current image to operate on
	 */
    private ImagePlus currentImage = null;
    
    /**
     * The kernel function
     */
    private CUfunction kernelFunction = null;
    
    @Override
    public void run(ImageProcessor imageProcessor) 
    {
    	int[] pixels = (int[])imageProcessor.getPixels();
        int w = imageProcessor.getWidth();
        int h = imageProcessor.getHeight();
        execute(pixels, w, h);
        currentImage.updateAndDraw();
    }

    /**
     * Will execute the CUDA kernel with the given parameters
     * 
     * @param pixels An array containing the pixels of the
     * image as RGB integers
     * @param w The width of the image
     * @param h The height of the image
     */
    void execute(int pixels[], int w, int h)
    {
        if (kernelFunction == null)
        {
            IJ.showMessage("Error", "The kernel was not initialized");
            return;
        }
        
    	// Allocate memory on the device, and copy the host data to the device 
        int size = w * h * Sizeof.INT;
        CUdeviceptr pointer = new CUdeviceptr();
        cuMemAlloc(pointer, size);
        cuMemcpyHtoD(pointer, Pointer.to(pixels), size);
        
        // Set up the kernel parameters: A pointer to an array
        // of pointers which point to the actual values.
        Pointer kernelParameters = Pointer.to(
            Pointer.to(pointer),
            Pointer.to(new int[] { w }), 
            Pointer.to(new int[] { h })        
        );

        // Call the kernel function
        int blockSize = 16;
        int gridSize = (Math.max(w, h) + blockSize - 1) / blockSize;
        cuLaunchKernel(kernelFunction,
            gridSize, gridSize, 1,   // Grid dimension
            blockSize, blockSize, 1, // Block dimension
            0, null,                 // Shared memory size and stream
            kernelParameters, null   // Kernel- and extra parameters
        );
        cuCtxSynchronize();        
        
        // Copy the data from the device back to the host and clean up
        cuMemcpyDtoH(Pointer.to(pixels), pointer, size);
        cuMemFree(pointer);
    }

    @Override
    public int setup(String arg, ImagePlus imagePlus)
    {
    	if (arg != null && arg.equals("about"))
    	{
            IJ.showMessage(
                "About JCuda ImageJ Plugin...",
                "An example of an ImageJ plugin using JCuda\n");
    		return DOES_RGB;
    	}
        currentImage = imagePlus;

        // Enable exceptions and omit all subsequent error checks
        JCudaDriver.setExceptionsEnabled(true);
        JNvrtc.setExceptionsEnabled(true);

        // Initialize the driver and create a context for the first device.
        cuInit(0);
        CUdevice device = new CUdevice();
        cuDeviceGet(device, 0);
        CUcontext context = new CUcontext();
        cuCtxCreate(context, 0, device);
        
        // Obtain the CUDA source code from the CUDA file
        String cuFileName = "JCudaImageJExampleKernel.cu";
        String sourceCode = readResourceAsString(cuFileName);
        if (sourceCode == null)
        {
            IJ.showMessage("Error",
                "Could not read the kernel source code");
            return DOES_RGB;
        }
        
        // Create the kernel function
        this.kernelFunction = createFunction(sourceCode, "invert");
    	
    	return DOES_RGB;
    }
    
    /**
     * Create the CUDA function object for the kernel function with the
     * given name that is contained in the given source code
     * 
     * @param sourceCode The source code
     * @param kernelName The kernel function name
     * @return
     */
    private static CUfunction createFunction(
        String sourceCode, String kernelName)
    {
        // Use the NVRTC to create a program by compiling the source code
        nvrtcProgram program = new nvrtcProgram();
        nvrtcCreateProgram(
            program, sourceCode, null, 0, null, null);
        nvrtcCompileProgram(program, 0, null);
        
        // Obtain the compilation log, and print it if it is not empty
        // (for the case there are any warnings)
        String programLog[] = new String[1];
        nvrtcGetProgramLog(program, programLog);
        String log = programLog[0].trim();
        if (!log.isEmpty())
        {
            System.err.println("Program compilation log:\n" + log);
        }
        
        // Obtain the PTX ("CUDA Assembler") code of the compiled program
        String[] ptx = new String[1];
        nvrtcGetPTX(program, ptx);
        nvrtcDestroyProgram(program);

        // Create a CUDA module from the PTX code
        CUmodule module = new CUmodule();
        cuModuleLoadData(module, ptx[0]);

        // Obtain the function pointer to the kernel function from the module
        CUfunction function = new CUfunction();
        cuModuleGetFunction(function, module, kernelName);
     
        return function;
    }
    
    
    /**
     * Read the resource with the given name, and return its contents as
     * a string. Returns <code>null</code> if the resource cannot be found
     * or read.
     * 
     * @param name The name of the resource
     * @return The contents of the resource
     */
    private static String readResourceAsString(String name)
    {
        InputStream inputStream = 
            JCuda_ImageJ_Example_Plugin.class.getResourceAsStream(name);
        if (inputStream == null)
        {
            IJ.showMessage("Error",
                "Resource was not found:\n" + name);
            return null;
        }
        try
        {
            return readStreamAsString(inputStream);
        }
        catch (IOException e)
        {
            IJ.showMessage("Error",
                "Could not read the resource:\n" + e.getMessage());
            return null;
        }
    }
    
    /**
     * Read the contents of the given input stream, and return it as a string
     * 
     * @param inputStream The input stream
     * @return The string
     * @throws IOException If the input cannot be read
     */
    private static String readStreamAsString(
        InputStream inputStream) throws IOException
    {
        try(Scanner s = new Scanner(inputStream)) 
        { 
            Scanner scanner = s.useDelimiter("\\A");
            if (scanner.hasNext()) 
            {
                return s.next();
            }
            throw new IOException("Could not read input stream"); 
        }        
    }

}