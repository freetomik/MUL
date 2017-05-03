/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package guitartuner;

import com.synthbot.jasiohost.AsioDriverListener;
import com.synthbot.jasiohost.AsioChannel;
import com.synthbot.jasiohost.AsioDriver;
import com.synthbot.jasiohost.AsioDriverState;
import com.synthbot.jasiohost.AsioException;
import java.util.Set;
import org.jtransforms.fft.DoubleFFT_1D;

/**
 *
 * @author Tomáš
 */
public class ToneRecognizer implements AsioDriverListener {


	// taken from Bachelor thesis at https://www.vutbr.cz/studium/zaverecne-prace?zp_id=88462
    private AsioDriver asioDriver;
    private Set<AsioChannel> activeChannels;
    private int[] index;
    private int bufferSize;
    private double sampleRate;
    private float[] output;
    private double[] outputTest;
    private DoubleFFT_1D fft;
    private double[][] fftBuffer;
    private static int fftBufferSize;
    private int bufferCount;
    private AsioDriverListener host;
    private boolean runRecognizer;
    public static double thresholdValue;
    GUIController controller;
    
	private int sampleIndex;
	private double sinusFreq;
   
	// TODO: add constructor
	public ToneRecognizer() {
		bufferSize = 512;
		sampleRate = 44100.0;
		sampleIndex = 0;
		sinusFreq = 440.0;
        fftBufferSize = 16384;
		outputTest = new double[fftBufferSize];
        fft = new DoubleFFT_1D(fftBufferSize);
	}


	@Override
	public void sampleRateDidChange(double sampleRate) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void resetRequest() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void resyncRequest() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void bufferSizeChanged(int bufferSize) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void latenciesChanged(int inputLatency, int outputLatency) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	// taken from Bachelor thesis at https://www.vutbr.cz/studium/zaverecne-prace?zp_id=88462
	@Override
	public void bufferSwitch(long sampleTime, long samplePosition, Set<AsioChannel> activeChannels) {
        for (AsioChannel channelInfo : activeChannels) {
            if(runRecognizer){
                if (!channelInfo.isInput()){
//                    if(controller.canPlay()){
                        channelInfo.write(output);
//                    }
                }
                else{
                    if(channelInfo.getChannelIndex()==0){
                        channelInfo.read(output);
                        for(int i=0;i<bufferSize; i++){
                            for(int j=0;j<bufferCount;j++){
                                if (index[j]==fftBufferSize)
                                    break;
                            }
                            for(int j=0;j<bufferCount;j++){
                                fftBuffer[j][index[j]] = output[i];
                            }
                            for(int j=0;j<bufferCount;j++){
                                index[j]++;
                            }
                        }
                        for(int i=0;i<bufferCount;i++){
                            if (index[i]== fftBufferSize){
                                fftBuffer[i] = applyHannWindow(fftBuffer[i]);
                                fft.realForward(fftBuffer[i]);
                                double[] fftData = fftAbs(fftBuffer[i]);                         

								int baseFrequencyIndex = getBaseFrequencyIndex(fftData);
								double baseFrequency = getFrequencyForIndex(baseFrequencyIndex, fftData.length, (int)sampleRate);
								// controller.updateFreqLabel(baseFrequency);
                                index[i]=0;
                            }
                        }
                    }
                }
            }
            else{
                if (!channelInfo.isInput()){
                    for(int i=0;i<output.length;i++)
                        output[i] = 0;
                    channelInfo.write(output);
                }
            }
        }
	}

	public void setSinFreq(double f) { sinusFreq = f; }

	public void setSampleRate(double s) { sampleRate = s; }

	public void setFFTBufSize(int b) { fftBufferSize = b; }

	public double[] generateSinus() {
		for (int i = 0; i < fftBufferSize; i++, sampleIndex++)
		  outputTest[i] = (double) Math.sin(2 * Math.PI * sinusFreq * sampleIndex / sampleRate);
		return outputTest;
	}
	
	public double tune() {
		double[] buffer = generateSinus();
		buffer = applyHannWindow(buffer);
		fft.realForward(buffer);
		double[] fftData = fftAbs(buffer);                         

		int baseFrequencyIndex = getBaseFrequencyIndex(fftData);
		double baseFrequency = getFrequencyForIndex(baseFrequencyIndex, fftData.length, (int)sampleRate);

		return baseFrequency;
	}
	

	// taken from Bachelor thesis at https://www.vutbr.cz/studium/zaverecne-prace?zp_id=88462
    private static double[] fftAbs(double[] buffer){
        double[] fftAbs = new double[fftBufferSize/2]; 
        for(int i=0;i<fftBufferSize/2;i++){
            double re = buffer[2*i];
            double im = buffer[2*i+1];
            fftAbs[i] = Math.sqrt(re*re+im*im);
        }
        return fftAbs;
    }
    
	// taken from Bachelor thesis at https://www.vutbr.cz/studium/zaverecne-prace?zp_id=88462
    public double[] applyHannWindow(double[] input){
        double[] out = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            double mul = 0.5 * (1 - Math.cos(2*Math.PI*i/input.length-1));
            out[i] = mul * input[i];
        }
        return out;
    }

	private int getBaseFrequencyIndex(double[] spectrum) {
		double maxVal = Double.NEGATIVE_INFINITY;
		int maxInd = 0;
		for(int i = 0; i < spectrum.length; i++) {
			if(maxVal < spectrum[i]) {
				maxVal = spectrum[i];
				maxInd = i;
			}
		}
		return maxInd;
	}

	// taken from https://gist.github.com/akuehntopf/4da9bced2cb88cfa2d19, author Andreas Kühntopf
	private float getFrequencyForIndex(int index, int size, int rate) {
		return (float)index * (float)rate / (float)size;
}
    
	// taken from Bachelor thesis at https://www.vutbr.cz/studium/zaverecne-prace?zp_id=88462
    public boolean startAsio(String driver, int refFreq){
        if (asioDriver == null) {
            try{
                asioDriver = AsioDriver.getDriver(driver);
                if (asioDriver.canSampleRate(48000))
                    asioDriver.setSampleRate(48000);
                asioDriver.addAsioDriverListener(host);
                activeChannels.add(asioDriver.getChannelOutput(0));
                activeChannels.add(asioDriver.getChannelOutput(1));

                activeChannels.add(asioDriver.getChannelInput(0));
                activeChannels.add(asioDriver.getChannelInput(1));
                bufferSize = asioDriver.getBufferPreferredSize();	// 512 by default
                sampleRate = asioDriver.getSampleRate();			// 44100.0 by default
                output = new float[bufferSize];
//                reInitTonesAndChords(refFreq);
                asioDriver.createBuffers(activeChannels);
                asioDriver.start();
                return true;
            }
            catch(AsioException e) {
                System.out.println("No driver available.");
                return false;
            }
        }
        return false;
      }
    
    public void shutdownDriver(){
        if (asioDriver != null) {
                asioDriver.shutdownAndUnloadDriver();
                asioDriver = null;
            }
    }
    
    public void openAsioSettings(){
        if (asioDriver != null && 
            asioDriver.getCurrentState().ordinal() >= AsioDriverState.INITIALIZED.ordinal()) {
            asioDriver.openControlPanel();          
        }
    }
    
}