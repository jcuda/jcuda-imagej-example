/**
 * ImageJ Plugin using JCuda
 * 
 * Copyright (c) 2013-2018 Marco Hutter - http://www.jcuda.org
 */

import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;


/**
 * A "standalone" test for the JCuda ImageJ Plugin. Loads an image
 * file, applies the JCuda_ImageJ_Example_Plugin to the file, and 
 * shows the result
 */
public class JCudaImageJExamplePluginTest 
{
    /**
     * The entry point of this test
     * 
     * @param args Not used
     * @throws IOException If an IO error occurs
     */
    public static void main(String args[]) throws IOException
    {
    	// Load an image, and store it in an image of
    	// type TYPE_INT_RGB
        String imageFileName = "lena512color.png";
        InputStream inputStream = 
            JCudaImageJExamplePluginTest.class.getResourceAsStream(
                imageFileName);
        final BufferedImage image = ImageIO.read(inputStream);
        int w = image.getWidth();
        int h = image.getHeight();
        final BufferedImage inputImage = 
            new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = inputImage.createGraphics();
        g.drawImage(image, 0,0, null);
        g.dispose();
        
        // Obtain the pixels if the image (which are 
        // now known to be int values with RGB colors)
        DataBuffer dataBuffer = inputImage.getRaster().getDataBuffer();
        DataBufferInt dataBufferInt = (DataBufferInt)dataBuffer;
        int pixels[] = dataBufferInt.getData();

        // Create and set up the plugin, and apply
        // it to the pixels
        JCuda_ImageJ_Example_Plugin plugin = new JCuda_ImageJ_Example_Plugin();
        plugin.setup(null, null);
        plugin.execute(pixels, w, h);
        
        // Show the resulting image
        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                JFrame f = new JFrame();
                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                f.getContentPane().setLayout(new GridLayout(1,0));
                f.getContentPane().add(new JLabel(new ImageIcon(image)));
                f.getContentPane().add(new JLabel(new ImageIcon(inputImage)));
                f.pack();
                f.setLocationRelativeTo(null);
                f.setVisible(true);
            }
        });
    }

}