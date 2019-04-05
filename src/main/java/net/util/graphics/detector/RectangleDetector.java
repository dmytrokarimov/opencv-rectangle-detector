package net.util.graphics.detector;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RectangleDetector {

	private static final Logger LOG = LoggerFactory.getLogger(RectangleDetector.class);

	// magic numbers - values was randomly set by hand
	private static final double MAX_LINE_HEIGHT = 0.98;
	private static final double OPENCV_FALSE_POSITIVE_INTERSECT_RECT_HEIGHT = 0.05;
	private static final double OPENCV_FALSE_POSITIVE_RECT_WIDTH = 0.5;
	private static final double MIN_LINE_WIDTH = 0.65;
	private static final double MIN_LINE_HEIGHT = 0.05;
	// ---

	private double maxLineHeight = MAX_LINE_HEIGHT;
	
	private double opencvFalsePositiveIntersectRectHeight = OPENCV_FALSE_POSITIVE_INTERSECT_RECT_HEIGHT;
	
	private double opencvFalsePositiveRectWidth = OPENCV_FALSE_POSITIVE_RECT_WIDTH;

	private double minLineWidth = MIN_LINE_WIDTH;
	
	private double minLineHeight = MIN_LINE_HEIGHT;

	/**
	 * Converts image (TYPE_BYTE_GRAY) to Mat
	 */
	public static Mat img2Mat(BufferedImage in) {
		Mat out = new Mat(in.getHeight(), in.getWidth(), CvType.CV_8UC(3));
		byte[] data = new byte[in.getWidth() * in.getHeight() * (int) out.elemSize()];
		int[] dataBuff = in.getRGB(0, 0, in.getWidth(), in.getHeight(), null, 0, in.getWidth());
		for (int i = 0; i < dataBuff.length; i++) {
			data[i * 3] = (byte) ((dataBuff[i]));
			data[i * 3 + 1] = (byte) ((dataBuff[i]));
			data[i * 3 + 2] = (byte) ((dataBuff[i]));
		}
		out.put(0, 0, data);
		
		return out;
	}
	
	private List<Rect> findLongLines(Mat gray, BufferedImage image, boolean horizontal) {
		Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, horizontal ? new Size(5, 2) : new Size(2, 5));
		Mat dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, 1));
		Mat d = new Mat();
		Mat thresh = new Mat();
		Mat close = new Mat();
		Mat hierarchy = new Mat();
		Mat dilate = new Mat();
		Mat ones = Mat.ones(new Size(5, 5), CvType.CV_8UC1);
		List<MatOfPoint> contours = new ArrayList<>();
		try {
			
			if (horizontal) {
				Imgproc.Sobel(gray, d, CvType.CV_16S, 0, 2);
			} else {
				Imgproc.Sobel(gray, d, CvType.CV_16S, 1, 0);
			}
			
			Core.convertScaleAbs(d, d);
			Core.normalize(d, d, 0, 255, Core.NORM_MINMAX);
			
			Imgproc.threshold(d, thresh, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
	
			Imgproc.morphologyEx(thresh, close, Imgproc.MORPH_DILATE, kernel);
			
			
			Imgproc.findContours(close, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
	
			for (MatOfPoint matOfPoint : contours) {
				Rect rect = Imgproc.boundingRect(matOfPoint);
				if ((horizontal ? (rect.width / rect.height) : (rect.height / rect.width)) > 5) { //magic number. Large numbers make line more smooth 
					Imgproc.drawContours(close, Arrays.asList(matOfPoint), 0, Scalar.all(255), -1);
				} else {
					Imgproc.drawContours(close, Arrays.asList(matOfPoint), 0, Scalar.all(0), -1);
				}
			}
			
			Imgproc.morphologyEx(close, close, Imgproc.MORPH_DILATE, dilateKernel, new Point(0, 0), 4);
			
			Imgproc.dilate(close, dilate, ones, new Point(0, 0), 3);
	
			contours.forEach(MatOfPoint::release);
			contours.clear();
			Imgproc.findContours(dilate, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
			
			return contours.stream().map(Imgproc::boundingRect)
					.filter(rect -> horizontal ? 
							(rect.width > image.getWidth() * MIN_LINE_WIDTH) : 
							(rect.height > image.getHeight() * MIN_LINE_HEIGHT && rect.height < image.getHeight() * MAX_LINE_HEIGHT))
					.collect(Collectors.toList());	
		} finally {
			kernel.release();
			dilateKernel.release();
			d.release();
			thresh.release();
			close.release();
			hierarchy.release();
			dilate.release();
			ones.release();
			contours.forEach(MatOfPoint::release);
		}
	}
	
	/**
	 * 
	 * @param image must have a type BufferedImage.TYPE_BYTE_GRAY
	 */
	public List<SplitRectangle> findRectangles(BufferedImage image, List<Rectangle> barcodes) {
		List<Rect> longLinesHorizontal, longLinesVertical;
		Mat input = null;
		Mat gray = null;

		try {
			input = img2Mat(image);

			if (input.channels() == 3) {
				gray = new Mat();
				Imgproc.cvtColor(input, gray, Imgproc.COLOR_BGR2GRAY);
			} else {
				gray = input;
			}
			longLinesHorizontal = findLongLines(gray, image, true);
			longLinesVertical = findLongLines(gray, image, false);
		} finally {
			if (input != null) {
				input.release();
			}
			if (gray != null) {
				gray.release();
			}
		}
		//find intersection rectangles
		List<Rectangle> hLines = longLinesHorizontal.stream().map(rect -> new Rectangle(rect.x, rect.y, rect.width, rect.height)).sorted((x1, x2) -> x1.y - x2.y).collect(Collectors.toList());
		List<Rectangle> vLines = longLinesVertical.stream()
				.map(rect -> new Rectangle(rect.x, rect.y, rect.width, rect.height))
				//lines at this position shouldn't be used, because really at this position can be only garbage 
				.filter(rect -> !(rect.x == 0 || rect.y == 0))
				.filter(rect -> !(rect.x + rect.width == image.getWidth()))
				.collect(Collectors.toList());
				
		List<Rectangle> splitRectangles = vLines
			//skip lines that don't intersect each other
			.stream()
			.collect(Collectors.toMap(
					vLine -> vLine, 
					vLine -> hLines.stream()
						.filter(vLine::intersects)
						//removes vertical garbage from horizontal line
						.map(hline -> {
							Rectangle rec = hline.intersection(vLine);
							rec.x = hline.x;
							rec.width = hline.width;
							return rec;
						})
						.collect(Collectors.toList())))
			//find first and last horizontal lines
			.entrySet().stream()
			.collect(Collectors.toMap(
					Map.Entry::getKey, 
					value -> {
						List<Rectangle> lines = value.getValue();
						lines = lines.stream().sorted((r1, r2) -> r1.y - r2.y).collect(Collectors.toList());
						return lines.isEmpty() ? lines : Arrays.asList(lines.get(0), lines.get(lines.size() - 1));
					}))
			.entrySet().stream()
			//union all same rectangles and we will finally find all unique rectangles  
			.map(entry -> entry.getValue().stream().reduce(entry.getKey(), Rectangle::union))
			//remove false positive
			.filter(rect -> rect.getWidth() > image.getWidth() * OPENCV_FALSE_POSITIVE_RECT_WIDTH)
			//collect only unique rectangle (rects that don't intersect each other or remove inner rectangles)
			.reduce(new ArrayList<Rectangle>(), (rectList, rect) -> {
				rectList.removeIf(existRect -> rect.intersection(existRect).getHeight() > rect.getHeight() * OPENCV_FALSE_POSITIVE_INTERSECT_RECT_HEIGHT && 
						rect.union(existRect).height * 0.5 <= rect.height);
				rectList.removeIf(existRect -> rect.contains(rect.x, existRect.y) && rect.contains(rect.x, existRect.y + existRect.height));
				rectList.add(rect);
				return rectList;
			}, (l1, l2) -> {l1.addAll(l2); return l1;});
		
		new ArrayList<>(splitRectangles).forEach(rect -> {
			splitRectangles.removeIf(existRect -> !rect.equals(existRect) && rect.contains(rect.x, existRect.y) && rect.contains(rect.x, existRect.y + existRect.height));
		});
		
		LOG.debug("Found rectangles: " + splitRectangles);
		
		List<Rectangle> verticalBarcodes = barcodes.stream()
			.reduce(new ArrayList<Rectangle>(), (list, bar) -> {
				//filter out horizontal barcodes
				if (!list.stream().filter(bar2 -> !bar.equals(bar2))
							.filter(bar2 -> bar.union(new Rectangle(bar.x, bar.y, image.getWidth(), 1)).intersects(bar2))
							.findAny()
							.isPresent()) {
					list.add(bar);
				}
				return list;
			}, (l1, l2) -> {
				l1.addAll(l2);
				return l2;
			})
			.stream()
			.sorted((x1, x2) -> x1.y - x2.y)
			.collect(Collectors.toList());
		
		if (splitRectangles.isEmpty()) {
			LOG.debug("Split position didn't found, trying to find splitting position using all page");
			splitRectangles.add(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
		}
		
		//found less rectangles than barcodes on a page
		if (verticalBarcodes.size() > splitRectangles.stream().filter(rec -> verticalBarcodes.stream().filter(rec::intersects).findAny().isPresent()).count()) {
			LOG.debug("Trying to split rectangles using barcodes position");
			
			Rectangle splitRectangle = null;
			
			for (Rectangle bar : verticalBarcodes) {
				if (splitRectangle == null || !splitRectangle.intersects(bar)) {
					splitRectangle = splitRectangles.stream().filter(bar::intersects).findAny().orElse(null);
				} else {
					if (splitRectangle.intersects(bar)) {
						Rectangle line = hLines.stream()
								.filter(r1 -> r1.y <= bar.y)
								.sorted((x1, x2) -> x2.y - x1.y)
								.findFirst()
									.orElseGet(() -> new Rectangle(bar.x, bar.y - 1, image.getWidth(), 1));
						splitRectangles.add(new Rectangle(splitRectangle.x, splitRectangle.y, splitRectangle.width, line.y - splitRectangle.y));
						Rectangle newSplitRectangle = new Rectangle(splitRectangle.x, line.y, splitRectangle.width, splitRectangle.height - (line.y - splitRectangle.y));
						splitRectangles.add(newSplitRectangle);
						splitRectangles.remove(splitRectangle);
						splitRectangle = newSplitRectangle;
					}
				}
				
				if (splitRectangle == null) {
					//nothing to do, we can't find spliting rectangle
					continue;
				}
			}
			LOG.debug("Found rectangles using barcode position: " + splitRectangles);
		}
		
		List<SplitRectangle> splitRectanglesList = splitRectangles
				.stream()
				.sorted((r1, r2) -> r1.y - r2.y)
				.map(rect -> {
					SplitRectangle sRect = new SplitRectangle();
					sRect.rect = rect;
					return sRect;
				})
				.collect(Collectors.toList());
		
		//check bottom line - this needed to find rollcartes that split onto 2 pages
		if (!splitRectanglesList.isEmpty()) {
			SplitRectangle lastRect = splitRectanglesList.get(splitRectanglesList.size() - 1);
			List<Rectangle> reverseHLines = new ArrayList<>(hLines);
			Collections.reverse(reverseHLines);
			reverseHLines.stream().filter(lastRect.rect::intersects).findFirst().ifPresent(hLine -> {
				Rectangle bottomLineExpect = new Rectangle(lastRect.rect.x, lastRect.rect.y + lastRect.rect.height - hLine.height, lastRect.rect.width, hLine.height);
				lastRect.bottomLineFound = bottomLineExpect.intersects(hLine);
			});
		}
		
		return splitRectanglesList;
	}
	
	public static class SplitRectangle {
		Rectangle rect;
		boolean bottomLineFound = true;
	}
	
	public double getMaxLineHeight() {
		return maxLineHeight;
	}

	public void setMaxLineHeight(double maxLineHeight) {
		this.maxLineHeight = maxLineHeight;
	}

	public double getOpencvFalsePositiveIntersectRectHeight() {
		return opencvFalsePositiveIntersectRectHeight;
	}

	public void setOpencvFalsePositiveIntersectRectHeight(double opencvFalsePositiveIntersectRectHeight) {
		this.opencvFalsePositiveIntersectRectHeight = opencvFalsePositiveIntersectRectHeight;
	}

	public double getOpencvFalsePositiveRectWidth() {
		return opencvFalsePositiveRectWidth;
	}

	public void setOpencvFalsePositiveRectWidth(double opencvFalsePositiveRectWidth) {
		this.opencvFalsePositiveRectWidth = opencvFalsePositiveRectWidth;
	}

	public double getMinLineWidth() {
		return minLineWidth;
	}

	public void setMinLineWidth(double minLineWidth) {
		this.minLineWidth = minLineWidth;
	}

	public double getMinLineHeight() {
		return minLineHeight;
	}

	public void setMinLineHeight(double minLineHeight) {
		this.minLineHeight = minLineHeight;
	}
}
