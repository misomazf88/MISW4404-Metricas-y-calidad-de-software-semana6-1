package gui;
import ij.*;
import macro.Interpreter;
import measure.*;
import plugin.filter.Analyzer;
import process.*;

import java.awt.*;
import java.awt.image.*;

public class HistogramPlot extends ImagePlus {
	
	private Draw draw = new Draw();
	static final double SCALE = Prefs.getGuiScale();
	static final int HIST_WIDTH = (int)(SCALE*256);
	static final int HIST_HEIGHT = (int)(SCALE*128);
	static final int XMARGIN = (int)(20*SCALE);
	static final int YMARGIN = (int)(10*SCALE);
	static final int WIN_WIDTH = HIST_WIDTH + (int)(44*SCALE);
	static final int WIN_HEIGHT = HIST_HEIGHT + (int)(118*SCALE);
	static final int BAR_HEIGHT = (int)(SCALE*12);
	static final int INTENSITY1=0, INTENSITY2=1, RGB=2, RED=3, GREEN=4, BLUE=5;
	static final Color frameColor = new Color(30,60,120);
	
	int rgbMode = -1;
	ImageStatistics stats;
	boolean stackHistogram;
	Calibration cal;
	long[] histogram;
	LookUpTable lut;
	int decimalPlaces;
	int digits; 
	long newMaxCount;
	boolean logScale;
	int yMax;
	int srcImageID;
	Rectangle frame;
	Font font = new Font("SansSerif",Font.PLAIN,(int)(12*SCALE));
	boolean showBins;
	int col1, col2, row1, row2, row3, row4, row5;
	    
	public HistogramPlot() {
		setImage(NewImage.createRGBImage("Histogram", WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
	}

	/** Plots a histogram using the specified title and number of bins. 
		Currently, the number of bins must be 256 expect for 32 bit images. */
	public void draw(String title, ImagePlus imp, int bins) {
		draw(imp, bins, 0.0, 0.0, 0);
	}

	/** Plots a histogram using the specified title, number of bins and histogram range.
		Currently, the number of bins must be 256 and the histogram range range must be 
		the same as the image range expect for 32 bit images. */
	public void draw(ImagePlus imp, int bins, double histMin, double histMax, int yMax) {
		boolean limitToThreshold = (Analyzer.getMeasurements()&LIMIT)!=0;
		ImageProcessor ip = imp.getProcessor();
		if (ip.getMinThreshold()!=ImageProcessor.NO_THRESHOLD
		&& ip.getLutUpdateMode()==ImageProcessor.NO_LUT_UPDATE)
			limitToThreshold = false;  // ignore invisible thresholds
		if (imp.isRGB() && rgbMode<INTENSITY1)
			rgbMode=INTENSITY1;
		if (rgbMode==RED||rgbMode==GREEN||rgbMode==BLUE) {
			int channel = rgbMode - 2;
			ColorProcessor cp = (ColorProcessor)imp.getProcessor();
			ip = cp.getChannel(channel, null);
			ImagePlus imp2 = new ImagePlus("", ip);
			imp2.setRoi(imp.getRoi());
			stats = imp2.getStatistics(AREA+MEAN+MODE+MIN_MAX, bins, histMin, histMax);
		} else if (rgbMode==RGB)
			stats = RGBHistogram(imp, bins, histMin, histMax);
		else
			stats = imp.getStatistics(AREA+MEAN+MODE+MIN_MAX+(limitToThreshold?LIMIT:0), bins, histMin, histMax);
		draw(imp, stats);
	}
	
	private ImageStatistics RGBHistogram(ImagePlus imp, int bins, double histMin, double histMax) {
		ImageProcessor ip = (ColorProcessor)imp.getProcessor();
		ip = ip.crop();
		int w = ip.getWidth();
		int h = ip.getHeight();
		ImageProcessor ip2 = new ByteProcessor(w*3, h);
		ByteProcessor temp = null;
		for (int i=0; i<3; i++) {
			temp = ((ColorProcessor)ip).getChannel(i+1,temp);
			ip2.insert(temp, i*w, 0);
		}
		ImagePlus imp2 = new ImagePlus("imp2", ip2);
		return imp2.getStatistics(AREA+MEAN+MODE+MIN_MAX, bins, histMin, histMax);
	}

	/** Draws the histogram using the specified title and ImageStatistics. */
	public void draw(ImagePlus imp, ImageStatistics stats) {
		if (imp.isRGB() && rgbMode<INTENSITY1)
			rgbMode=INTENSITY1;
		stackHistogram = stats.stackStatistics;
		this.stats = stats;
		this.yMax = stats.histYMax;
		cal = imp.getCalibration();
		boolean limitToThreshold = (Analyzer.getMeasurements()&LIMIT)!=0;
		imp.getMask();
		histogram = stats.getHistogram();
		lut = imp.createLut();
		int type = imp.getType();
		boolean fixedRange = type==ImagePlus.GRAY8 || type==ImagePlus.COLOR_256 || imp.isRGB();
		ip.setColor(Color.white);
		ip.resetRoi();
		ip.fill();
		ImageProcessor srcIP = imp.getProcessor();
		draw.drawHistogram(imp, ip, fixedRange, stats.histMin, stats.histMax);
	}
	
	protected void drawHistogram(ImageProcessor ip, boolean fixedRange) {
		draw.drawHistogram(null, ip, fixedRange, 0.0, 0.0);
	}
       
	void drawAlignedColorBar(ImagePlus imp, double xMin, double xMax, ImageProcessor ip, int x, int y, int width, int height) {
		draw.drawAlignedColorBar(imp, xMin, xMax, ip, x, y, width, height);
	}

	/** Scales a threshold level to the range 0-255. */
	int scaleDown(ImageProcessor ip, double threshold) {
		return draw.scaleDown(ip, threshold);
	}

	void drawPlot(long maxCount, ImageProcessor ip) {
		draw.drawPlot(maxCount,ip);
		ip.setColor(frameColor);
		ip.drawRect(frame.x-1, frame.y, frame.width+2, frame.height+1);
		ip.setColor(Color.black);
	}
		
	void drawLogPlot (long maxCount, ImageProcessor ip) {
		draw.drawLogPlot(maxCount, ip);
	}
		
	void drawText(ImageProcessor ip, int x, int y, boolean fixedRange) {
		draw.drawText(ip,x,y,fixedRange);
	}
	
	private String d2s(double d) {
		if ((int)d==d)
			return IJ.d2s(d, 0);
		else
    		return IJ.d2s(d, 3, 8);
    }
	
	int getWidth(double d, ImageProcessor ip) {
		return ip.getStringWidth(d2s(d));
	}
				
	public int[] getHistogram() {
		int[] hist = new int[histogram.length];
		for (int i=0; i<histogram.length; i++)
			hist[i] = (int)histogram[i];
		return hist;
	}

	public double[] getXValues() {
		double[] values = new double[stats.nBins];
		for (int i=0; i<stats.nBins; i++)
			values[i] = cal.getCValue(stats.histMin+i*stats.binSize);
		return values;
	}
	
    @Override
    public void show() {
		if (IJ.isMacro()&&Interpreter.isBatchMode())
			super.show();
		else
			new HistogramWindow(this, WindowManager.getImage(srcImageID));
	}
	
}
