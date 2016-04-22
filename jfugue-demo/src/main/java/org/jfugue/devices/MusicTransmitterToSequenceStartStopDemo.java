/*
 * JFugue, an Application Programming Interface (API) for Music Programming
 * http://www.jfugue.org
 *
 * Copyright (C) 2003-2014 David Koelle
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jfugue.devices;

import java.util.Scanner;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;

import org.jfugue.demo.DemoPrint;
import org.jfugue.devtools.MidiDevicePrompt;
import org.jfugue.midi.MidiParser;
import org.staccato.StaccatoParserListener;

public class MusicTransmitterToSequenceStartStopDemo {
    public static void main(String[] args) {
        DemoPrint.start("Example listening to music from an external keyboard for a set amount of time.");
        MidiDevice device = MidiDevicePrompt.askForMidiDevice();
        if (device == null) {
            return;
        }
        
        try {
            MusicTransmitterToSequence transmitter = new MusicTransmitterToSequence(device);
            DemoPrint.step("Press [ENTER] when you're ready to start playing...");
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();
            transmitter.startListening();

            DemoPrint.step("Now, press [ENTER] when you're ready to stop playing...");
            scanner.nextLine();
            scanner.close();
            transmitter.stopListening();
            
            MidiParser parser = new MidiParser();
            StaccatoParserListener spl = new StaccatoParserListener();
            parser.addParserListener(spl);
            parser.parse(transmitter.getSequence());
            
            DemoPrint.step("Here's the music you entered:");
            DemoPrint.step(spl.getPattern().toString());
        } catch (MidiUnavailableException e) {
            e.printStackTrace();
        } catch (InvalidMidiDataException e) {
        	e.printStackTrace();
        } 
    }
}
