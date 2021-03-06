package org.jfugue.midi;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import com.sun.media.sound.AudioSynthesizer;

public class AudioUtils {

    public static AudioInputStream getAudioInputStream(final Sequence sequence) throws MidiUnavailableException, InvalidMidiDataException, IOException {
        return getAudioInputStream(sequence, DEFAULT_PATCH_PROVIDER, DEFAULT_AUDIO_FORMAT, DEFAULT_INFO);
    }

    /**
     * Get AudioInputStream from a Sequence.
     * Returns null if there is no available audio synthesizer.
     */
    public static AudioInputStream getAudioInputStream(final Sequence sequence, final PatchProvider patchProvider, final AudioFormat format, final Map<String, Object> info) throws MidiUnavailableException, InvalidMidiDataException, IOException
    {
    	assert(sequence != null);
    	assert(patchProvider != null);
    	assert(format != null);

        // Load soundbank
        final AudioSynthesizer synth = findAudioSynthesizer();

        if (synth == null) {
            return null;
        }

        patchProvider.loadPatchesIntoSynthesizer(synth);

//        AudioSynthesizer audioSynth = new SoftSynthesizer();
        AudioInputStream stream = synth.openStream(format, info);

        MidiTools.sendSequenceToReceiver(sequence, synth.getReceiver());

        synth.close();

        // Calculate how long the audio file needs to be.
        final long lengthInFrames = (long) (stream.getFormat().getFrameRate() * (sequence.getMicrosecondLength() / 1000000.0));
        stream = new AudioInputStream(stream, stream.getFormat(), lengthInFrames);

        return stream;
    }

    /** TODO: This method is similar to the JFugue 4.0.3 code and is here for debugging purposes only.
     * In JFugue 5, this should be removed and getAudioInputStream() should be used. */
    @Deprecated
    public static AudioInputStream getAudioInputStream_old(final Sequence sequence, final PatchProvider patchProvider, AudioFormat format, final Map<String, Object> info) throws MidiUnavailableException, InvalidMidiDataException, IOException
    {
        final Synthesizer synth = findAudioSynthesizer_old();
        if (synth == null) {
            throw new RuntimeException("No AudioSynthesizer was found!");
        }

        // If no format was specified, let use 44100 Hz, Stereo, 16 bit signed
        if(format == null)
            format = new AudioFormat(44100, 16, 2, true, false);

        Method openStreamMethod = null;
        try
        {
            openStreamMethod =
                synth.getClass().getMethod("openStream",
                        new Class[] {AudioFormat.class, Map.class});
        }
        catch(final NoSuchMethodException e)
        {
            throw new RuntimeException(e.getMessage());
        }

        AudioInputStream stream;
        try {
            stream = (AudioInputStream)
                openStreamMethod.invoke(synth, new Object[] {format, info});
        } catch (final IllegalArgumentException e) {
            throw new RuntimeException(e.getMessage());
        } catch (final IllegalAccessException e) {
            throw new RuntimeException(e.getMessage());
        } catch (final InvocationTargetException e) {
            throw new RuntimeException(e.getMessage());
        }


        // Play Sequence into AudioSynthesizer Receiver.
        final double total = send_old(sequence, synth.getReceiver());

        synth.close();

        // Calculate how long the audio file needs to be.
        final long len = (long) (stream.getFormat().getFrameRate() * (total + 4));
        stream = new AudioInputStream(stream, stream.getFormat(), len);

        return stream;
    }

    /** TODO: This method is called by getAudioInputStream_old() and is here for debugging purposes only */
    @Deprecated
    private static Synthesizer findAudioSynthesizer_old() throws MidiUnavailableException
    {
        Class audioSynthesizerClass;
        try {
            audioSynthesizerClass = Class.forName("com.sun.media.sound.AudioSynthesizer");
        } catch (final ClassNotFoundException e) {
            // AudioSynthesizer not in classpath, return null
            return null;
        }

        // First check if default synthesizer is AudioSynthesizer.
        final Synthesizer synth = MidiSystem.getSynthesizer();
        if (audioSynthesizerClass.isInstance(synth)) {
            return synth;
        }

        // If default synthesizer is not AudioSynthesizer, check others.
        final MidiDevice.Info[] midiDeviceInfo = MidiSystem.getMidiDeviceInfo();
        for (int i = 0; i < midiDeviceInfo.length; i++) {
            final MidiDevice dev = MidiSystem.getMidiDevice(midiDeviceInfo[i]);
            if (audioSynthesizerClass.isInstance(dev)) {
                return (Synthesizer)dev;
            }
        }

        // No AudioSynthesizer was found, return null.
        return null;
    }

    /** TODO: Prefer to use MidiTools.sendSequenceToReceiver() - this code is here for debugging purposes only */
    @Deprecated
    private static double send_old(final Sequence seq, final Receiver recv)
    {
        final float divtype = seq.getDivisionType();
        assert (seq.getDivisionType() == Sequence.PPQ);
        final Track[] tracks = seq.getTracks();
        final int[] trackspos = new int[tracks.length];
        int mpq = 500000;
        final int seqres = seq.getResolution();
        long lasttick = 0;
        long curtime = 0;
        while (true) {
            MidiEvent selevent = null;
            int seltrack = -1;
            for (int i = 0; i < tracks.length; i++) {
                final int trackpos = trackspos[i];
                final Track track = tracks[i];
                if (trackpos < track.size()) {
                    final MidiEvent event = track.get(trackpos);
                    if (selevent == null
                            || event.getTick() < selevent.getTick()) {
                        selevent = event;
                        seltrack = i;
                    }
                }
            }
            if (seltrack == -1)
                break;
            trackspos[seltrack]++;
            final long tick = selevent.getTick();
            if (divtype == Sequence.PPQ)
                curtime += ((tick - lasttick) * mpq) / seqres;
            else
                curtime = (long) ((tick * 1000000.0 * divtype) / seqres);
            lasttick = tick;
            final MidiMessage msg = selevent.getMessage();
            if (msg instanceof MetaMessage) {
                if (divtype == Sequence.PPQ)
                    if (((MetaMessage) msg).getType() == 0x51) {
                        final byte[] data = ((MetaMessage) msg).getData();
                        mpq = ((data[0] & 0xff) << 16)
                                | ((data[1] & 0xff) << 8) | (data[2] & 0xff);
                    }
            } else {
                if (recv != null)
                    recv.send(msg, curtime);
            }
        }
        return curtime / 1000000.0;
    }


    /**
     * Attempts to find a Synthesizer capable of producing Audio (e.g., an AudioSynthesizer).
     * Can return null if no synthesizer can be found.
     */
    public static AudioSynthesizer findAudioSynthesizer() throws MidiUnavailableException
    {
        Class<?> audioSynthesizerClass;
        try {
            audioSynthesizerClass = Class.forName("com.sun.media.sound.AudioSynthesizer");
        } catch (final ClassNotFoundException e) {
            // AudioSynthesizer not in classpath, return null
            return null;
        }

        // First check if the default synthesizer is AudioSynthesizer. If it is, we're happy.
        final AudioSynthesizer synth = (AudioSynthesizer)MidiSystem.getSynthesizer();
        if (audioSynthesizerClass.isInstance(synth)) {
            return synth;
        }

        // If default synthesizer is not AudioSynthesizer, see of there are others that may be.
        final MidiDevice.Info[] midiDeviceInfo = MidiSystem.getMidiDeviceInfo();
        for (int i = 0; i < midiDeviceInfo.length; i++) {
            final MidiDevice dev = MidiSystem.getMidiDevice(midiDeviceInfo[i]);
            if (audioSynthesizerClass.isInstance(dev)) {
                return (AudioSynthesizer)dev;
            }
        }

        // No AudioSynthesizer was found, return null.
        return null;
    }


    public static final AudioFormat DEFAULT_AUDIO_FORMAT = new AudioFormat(44100, 16, 2, true, false);
    public static final Map<String, Object> DEFAULT_INFO = null;
    public static final PatchProvider DEFAULT_PATCH_PROVIDER = new PatchProvider(null, 0);
}
