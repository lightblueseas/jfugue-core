package org.jfugue.pattern;

import org.staccato.StaccatoUtil;

/**
 * This util takes replacement strings with dollar signs, like "$0q $1h $2w", and replaces each $ index with a
 * value from the array of candidates. $_ is replaced with the underscoreReplacement. Returns the resulting Pattern.
 * Current known users include ChordProgression and Intervals.
 * 
 */
public class ReplacementFormatUtil {
    public static Pattern replaceDollarsWithCandidates(String sequence, PatternProducer[] candidates, PatternProducer underscoreReplacement)
    {
        StringBuilder buddy = new StringBuilder();

        int posPrevDollar = -1;
        int posNextDollar = 0;
        
        while (posNextDollar < sequence.length()) {
            posNextDollar = StaccatoUtil.findNextOrEnd(sequence, '$', posPrevDollar);
            if (posPrevDollar+1 < sequence.length()) {
                buddy.append(sequence.substring(posPrevDollar+1, posNextDollar));
            }
            if (posNextDollar != sequence.length()) {
                String selectionString = sequence.substring(posNextDollar+1, posNextDollar+2);
                if (selectionString.equals("_")) {
                    // If the underscore replacement has tokens, then the stuff after $_ needs to be applied to each token in the underscore replacement!
                    String replacementTokens[] = underscoreReplacement.getPattern().toString().split(" ");
                    int nextSpaceInSequence = StaccatoUtil.findNextOrEnd(sequence, ' ', posNextDollar);
                    for (String token : replacementTokens) {
                        buddy.append(token);
                        buddy.append(sequence.substring(posNextDollar+2, nextSpaceInSequence));
                        buddy.append(" ");
                    }
                    posNextDollar = nextSpaceInSequence-1;
                } else {
                    int selection = Integer.parseInt(sequence.substring(posNextDollar+1, posNextDollar+2));
                    if (selection > candidates.length) {
                        throw new IllegalArgumentException("The selector $"+selection+" is greater than the number of items to choose from, which has "+candidates.length+" items.");
                    }
                    buddy.append(candidates[selection].getPattern());
                }
            }
            posPrevDollar = posNextDollar+1;
        }       
        
        return new Pattern(buddy.toString().trim());
    }

}
