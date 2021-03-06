/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 *
 * Created on 27.03.2021
 */
package com.christianfries.surveillancecamera;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * Compares two images and calculates a (kind of) probability that the images are different.
 * 
 * @author Christian Fries
 */
public class ImageCompare {

	/**
	 * Compares two images and calculates a (kind of) probability that the images are different.
	 * A number 0 indicates that the images are identical. A number 1 indicates that the images are different.
	 * 
	 * Since images come with an amount of noise that depends on the camera system and other parameters
	 * (variation in sunlight, etc.), you may have to determine a good threshold value. A guess is 0.18.
	 * 
	 * The difference is determines by calculating a correlation rho of the two images.
	 * The raw difference it then defined as (1-rho)/2.
	 * Since the correlation of low-light images may be much higher than the correlation of high contrast images,
	 * it also calculates the mean value of the image (between 0 and 1) and scales the raw difference (1-rho)/2
	 * with abs(0.5-mean)/2. This will adjust for changes in the light.
	 * 
	 * @param reference The reference image.
	 * @param image The image to be compared to the reference image.
	 * @return A (kind of) probability that the images are different.
	 * @throws IOException Thrown if the image processing fails.
	 */
	public double getImageDifference(final BufferedImage reference, final BufferedImage image) throws IOException {

		if(reference == null) {
			return Double.MAX_VALUE;
		}

//		final boolean isImageHasAlpha = reference.getAlphaRaster() != null;

		final byte[] pixelsReference = ((DataBufferByte) reference.getRaster().getDataBuffer()).getData();
		final byte[] pixelsImage = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

		final double meanReference = getImageMean(pixelsReference);
		final double meanImage = getImageMean(pixelsImage);

		//		double varReference = getImageVar(pixelsReference);

		double covarSum = 0;
		double varSumReference = 0;
		double varSumImage = 0;

		for(int i=0; i < pixelsReference.length/3; i++) {

			final int red1 = Byte.toUnsignedInt(pixelsReference[3*i+0]);
			final int green1 = Byte.toUnsignedInt(pixelsReference[3*i+1]);
			final int blue1 = Byte.toUnsignedInt(pixelsReference[3*i+2]);

			final int red2 = Byte.toUnsignedInt(pixelsImage[3*i+0]);
			final int green2 = Byte.toUnsignedInt(pixelsImage[3*i+1]);
			final int blue2 = Byte.toUnsignedInt(pixelsImage[3*i+2]);

			final double diff1 = (red1+green1+blue1)/(3.0*255.0)-meanReference;
			final double diff2 = (red2+green2+blue2)/(3.0*255.0)-meanImage;

			covarSum += diff1*diff2;
			varSumReference += diff1*diff1;
			varSumImage += diff2*diff2;
		}

		final double contrast = 2.0 * DoubleStream.builder().add(meanReference).add(meanImage).add(1.0-meanReference).add(1.0-meanImage).build().min().getAsDouble();

		final double correlation = covarSum / Math.sqrt(varSumReference*varSumImage);
		final double level = (1+contrast) /2.0  *  (1.0 - correlation) / 2.0;

		return level;
	}

	private static double getImageMean(final byte[] pixels) {
		// IntStream sum does not work if we have more than 2 Megapixels
		return IntStream.range(0, pixels.length).parallel().mapToLong(i -> Byte.toUnsignedInt(pixels[i])).sum() / 255.0 / pixels.length;
		//		return IntStream.range(0, pixels.length).parallel().mapToDouble(i -> (double)Byte.toUnsignedInt(pixels[i]) / 255.0).average().orElse(Double.NaN);
	}

	@SuppressWarnings("unused")
	private static double getImageMeanSquared(final byte[] pixels) {
		return IntStream.range(0, pixels.length).parallel().mapToLong(i -> {
			final long value = Byte.toUnsignedInt(pixels[i]);
			return value*value;
		}).sum() / 255.0 / pixels.length;
	}

	@SuppressWarnings("unused")
	private static double getImageSigma(BufferedImage image, double mean) {
		return Math.sqrt(LongStream.range(0, image.getHeight()).parallel().mapToDouble(i -> {
			double sumOfSquaresForRow = 0;
			final int y = (int)i;
			for (int x = 0; x < image.getWidth(); x++) {
				//Retrieving contents of a pixel
				final int pixel = image.getRGB(x,y);
				//Creating a Color object from pixel value
				final Color color = new Color(pixel, true);
				//Retrieving the R G B values
				final int red = color.getRed();
				final int green = color.getGreen();
				final int blue = color.getBlue();

				sumOfSquaresForRow += Math.pow((red+green+blue)/255.0/3.0 - mean,2);
			}
			return sumOfSquaresForRow;
		}).sum() / (image.getHeight() * image.getWidth()));
	}

	@SuppressWarnings("unused")
	private static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) throws IOException {
		final Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_FAST);
		final BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
		return outputImage;
	}
}
