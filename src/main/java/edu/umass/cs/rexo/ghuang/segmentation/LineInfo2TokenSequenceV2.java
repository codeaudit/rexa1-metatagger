package edu.umass.cs.rexo.ghuang.segmentation;

import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Token;
import edu.umass.cs.mallet.base.types.TokenSequence;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Some added features to the original LineInfo2TokenSequence to help identify the references
 * 
 * @author kzaporojets based on ghuang's version of LineInfo2TokenSequence
 * 
 */
public class LineInfo2TokenSequenceV2 extends Pipe implements Serializable
{
	private static final long serialVersionUID = 1L;

    LineInfo2TokenSequence tokenSequence ;


	public LineInfo2TokenSequenceV2()	{
        LineInfo2TokenSequence tokenSequence = new LineInfo2TokenSequence();
    }

	public Instance pipe(Instance carrier)
	{
		LineInfo[] oldData = (LineInfo[]) carrier.getData();
		Token[] dataTokens = new Token[oldData.length];
		Token[] sourceTokens = new Token[oldData.length];

        tokenSequence.computeFeatures(oldData);


		computeFeatures(oldData);
		
		for (int i = 0; i < oldData.length; i++) {
			dataTokens[i] = new Token(oldData[i].text);
			Iterator iter = oldData[i].presentFeatures.iterator();
			
			sourceTokens[i] = new Token(oldData[i].text);
			
//			System.out.println(oldData[i].text);
			
			while (iter.hasNext()) {
				String featName = (String) iter.next();
				dataTokens[i].setFeatureValue(featName, 1);
				
//				System.out.println("\t" + featName);
			}
			
		}

		TokenSequence dataTS = new TokenSequence(dataTokens);
		TokenSequence sourceTS = new TokenSequence(sourceTokens);

		carrier.setData(dataTS);
		carrier.setSource(sourceTS);

		if (isTargetProcessing()) {
			Token[] targetTokens = new Token[dataTokens.length];
			
			for (int i = 0; i < dataTokens.length; i++)
				targetTokens[i] = new Token(oldData[i].trueLabel);

			TokenSequence targetTS = new TokenSequence(targetTokens);
			carrier.setTarget(targetTS);
		}

		return carrier;
	}

	
	
	private void computeFeatures(LineInfo[] lineInfos)
	{
		computeLexiconFeatures(lineInfos);
		computeLayoutFeatures(lineInfos);
	}
	
	
	private static void computeLayoutFeatures(LineInfo[] lineInfos)
	{

        /*kzaporojets: some additional data with respect to layout include:
         - mode of the width (for all of the lines)
         - mode of llx a urx (for the lines on a particular page) to identify both of the columns
         - mode of vertical distance between the lines (the two first modes) for all the lines.

        */
        Map <Integer, Integer> verticalDistance = new HashMap<Integer, Integer>();
        Map <Integer, Integer> widthLine = new HashMap<Integer, Integer>();
        Map <Integer, HashMap<ColumnData, Integer>> columnsData = new HashMap<Integer,HashMap<ColumnData,Integer>>();

        /*kzaporojetes: end of additional data, further in the code it will get completed*/

        int prevPageNum = 0;
        int prevFontNum = -1;
        int sumLineLengths = 0;
        int[] dist2prevLine = new int[lineInfos.length];
        HashMap refFontCounts = new HashMap();



        for (int i = 0; i < lineInfos.length; i++) {
            if (lineInfos[i].page != prevPageNum) {
                lineInfos[i].presentFeatures.add("newPage");
                prevPageNum = lineInfos[i].page;

                if (i > 0)
                    lineInfos[i-1].presentFeatures.add("lastLineOnPage");
            }

            else if (i > 0 && (lineInfos[i].llx > lineInfos[i-1].llx && lineInfos[i].lly > lineInfos[i-1].lly))
                lineInfos[i].presentFeatures.add("newColumn");
            else if (i > 0 && lineInfos[i].llx > lineInfos[i-1].llx)
                lineInfos[i].presentFeatures.add("indentedFromPrevLine");
            else if (i > 0 && lineInfos[i].llx < lineInfos[i-1].llx)
                lineInfos[i].presentFeatures.add("unTabbedFromPrevLine");
            else if (i > 0 && lineInfos[i].llx == lineInfos[i-1].llx)
                lineInfos[i].presentFeatures.add("sameIndentationAsPrevLine");


            int width = lineInfos[i-1].urx - lineInfos[i].llx;

            if(widthLine.get(width)!=null)
            {
                widthLine.put(width,verticalDistance.get(width)+1);

            }


            if (lineInfos[i].page == prevPageNum && i>0 && lineInfos[i-1].lly < lineInfos[i].ury) {
                int vertDistance = lineInfos[i-1].lly - lineInfos[i].ury;
                if(verticalDistance.get(vertDistance)!=null)
                {
                    verticalDistance.put(vertDistance,verticalDistance.get(vertDistance)+1);
                }
                else
                {
                    verticalDistance.put(vertDistance,1);
                }
            }


            if (lineInfos[i].multibox)
                lineInfos[i].presentFeatures.add("containsMultiFonts");

            if (i == 0)
                prevFontNum = lineInfos[i].font;
            else if (lineInfos[i].font != prevFontNum) {
                prevFontNum = lineInfos[i].font;
                lineInfos[i].presentFeatures.add("startsNewFont");
            }

            if (lineInfos[i].presentFeatures.contains("beginBrackets")
                    || (! lineInfos[i].presentFeatures.contains("seqHasBeginBrackets")
                    && lineInfos[i].presentFeatures.contains("beginsNumberCapital"))
                    || (! lineInfos[i].presentFeatures.contains("seqHasBeginBrackets")
                    && ! lineInfos[i].presentFeatures.contains("seqHasBeginNumberCapital")
                    && lineInfos[i].presentFeatures.contains("beginsCapitalInitials"))) {

                String fontNum = Integer.toString(lineInfos[i].font);

                if (! refFontCounts.containsKey(fontNum))
                    refFontCounts.put(fontNum, "0");
                int newCount = 1 + Integer.parseInt((String) refFontCounts.get(fontNum));
                refFontCounts.put(fontNum, Integer.toString(newCount));
            }

            sumLineLengths += lineInfos[i].urx - lineInfos[i].llx;

            if (i > 0)
                dist2prevLine[i] = Math.abs(lineInfos[i-1].lly - lineInfos[i].lly);
        }

        final int tolerance = 1; // difference in baseline y-coordinates must be greater than this to have "bigVertSpaceBefore" feature
        double avgLineLength = sumLineLengths / lineInfos.length;

        // Find the most common font number for probable reference begin lines
        int refFont = -1;
        int maxCount = 0;
        Iterator iter = refFontCounts.keySet().iterator();

        while (iter.hasNext()) {
            String key = (String) iter.next();
            int fontNum = Integer.parseInt(key);
            int count = Integer.parseInt((String) refFontCounts.get(key));

            if (count > maxCount) {
                maxCount = count;
                refFont = fontNum;
            }
        }

        // A second pass of feature computations
        for (int i = 0; i < lineInfos.length; i++) {
            if (lineInfos[i].urx - lineInfos[i].llx <= 0.75 * avgLineLength)
                lineInfos[i].presentFeatures.add("shortLineLength");

            if (i > 0
                    && !lineInfos[i].presentFeatures.contains("newPage")
                    && !lineInfos[i].presentFeatures.contains("newColumn")
                    && dist2prevLine[i] - dist2prevLine[i-1] > tolerance)
                lineInfos[i].presentFeatures.add("bigVertSpaceBefore");

            if (lineInfos[i].font == refFont)
                lineInfos[i].presentFeatures.add("usesRefFont");
            else if (refFont != -1 && ! lineInfos[i].presentFeatures.contains("containsMultiFonts"))
                lineInfos[i].presentFeatures.add("doesntUseRefFont");
        }

        //also see if it is possible to get a notion where do they start (the columns)
	}


	private void computeLexiconFeatures(LineInfo[] lineInfos)
	{
        String[] keywords = { "Proceedings", "Proc\\.", "Conference",
                "Workshop", "Technical ", "Tech\\. ", "Report", "Symposium",
                "Symp\\.", "Journal", "Lecture ", "Lect\\. ", "Notes ",
                "Computer ", "Science " };
        // high correlation with non-bibliographic content
        String[] postwords = { "^[^A-Za-z]*Received[^A-Za-z]",
                "^[A-Za-z]*Figure(s)?[^A-Za-z]",
                "^[A-Za-z]*Table(s)?[^A-Za-z]", "^[A-Za-z]*Graph(s)?[^A-Za-z]",
                "We ", " we ", "She ", " she ", "He ", " he ", "Our ", " our ",
                "Her ", " her ", "His ", " his ", "These ", " these ", "Acknowledgements" };
        // moderate correlation with non-bibliographic content
        String[] lowPostwords = { "They ", " they ", "This ", " this ", " is ",
                " are ", " was ", " have ", " but ", "[a-z]+\\s+[a-z]+ed " };
        String[] months = {"January", "Jan\\.?\\s", "February", "Feb\\.?\\s", "March", "Mar\\.?\\s",
                "April", "Apr\\.?\\s", "May", "June", "Jun\\.?\\s", "July", "Jul\\.\\s?",  "August", "Aug\\.?\\s",
                "September", "Sept?\\.?\\s", "October", "Oct\\.?\\s",  "November", "Nov\\.?\\s", "December", "Dec\\.?\\s" };

        int numBeginBrackets = 0;
        int numBeginNumberCapital = 0;
        int numBeginCapInitials = 0;
        int numPages = 1;
        int prevPage = 0;

        for (int i = 0; i < lineInfos.length; i++) {

            if (i == 0)
                prevPage = lineInfos[i].page;
            else if (lineInfos[i].page != prevPage) {
                numPages++;
                prevPage = lineInfos[i].page;
            }


            String squishedText = lineInfos[i].text.replaceAll("\\s", "");

            if (squishedText.length() == 0) continue;

            int numPeriodCommas = specialPunctCounter(squishedText);

            if (numPeriodCommas == 0)
                lineInfos[i].presentFeatures.add("noSpecialPuncts");
            else if (numPeriodCommas > 3)
                lineInfos[i].presentFeatures.add("manySpecialPuncts");
            else
                lineInfos[i].presentFeatures.add("someSpecialPuncts");

            if (squishedText.matches("^\\[.+\\].*")) {
                lineInfos[i].presentFeatures.add("beginBrackets");
                numBeginBrackets++;
            }
            //kzaporojets: the numbering of some references start with parenthesis
            if (squishedText.matches("^\\(.+\\).*")) {
                lineInfos[i].presentFeatures.add("beginBrackets");
                numBeginBrackets++;
            }
            //kzaporojets: some other features
            if (squishedText.matches("^\\([0-9]+\\).*")) {
                lineInfos[i].presentFeatures.add("beginNumericBrackets");
                numBeginBrackets++;
            }

            //kzaporojets: end some other features
            if (squishedText.matches("^[0-9]+\\.?\\p{Lu}.*")) {
                lineInfos[i].presentFeatures.add("beginsNumberCapital");
                numBeginNumberCapital++;
            }
            if (! squishedText.endsWith("."))
                lineInfos[i].presentFeatures.add("noEndingPeriod");
            if (squishedText.matches(".*[^\\p{Ll}\\p{Lu}]\\p{Lu}\\.$"))
                lineInfos[i].presentFeatures.add("endsWithCapPeriod");
            if (squishedText.matches(".*[0-9]+-(-)?[0-9]+.*"))
                lineInfos[i].presentFeatures.add("containsPageRange");
            if (squishedText.matches(".*(19|20)\\d{2}.*"))
                lineInfos[i].presentFeatures.add("containsYear");
            if (squishedText.matches(".*(?i)appendix.*"))
                lineInfos[i].presentFeatures.add("containsAppendix");
            if (squishedText.matches(".*(?i)received.*"))
                lineInfos[i].presentFeatures.add("containsReceived");
            if (squishedText.matches(".*(?i)address.*"))
                lineInfos[i].presentFeatures.add("containsAddress");
            if (squishedText.matches(".*\\w+@\\w+\\.\\w+.*"))
                lineInfos[i].presentFeatures.add("containsEmail");
            if (squishedText.matches(".*(ftp|http)\\://\\w+\\.\\w+.*"))
                lineInfos[i].presentFeatures.add("containsURL");
            if (squishedText.matches(".*[,\\-\\:]$"))
                lineInfos[i].presentFeatures.add("endsWithPunctNotPeriod");
            if (squishedText.matches(".*\\d.*"))
                lineInfos[i].presentFeatures.add("containsDigit");

            if (lineInfos[i].text.matches(".*et(\\.)?\\sal.*"))
                lineInfos[i].presentFeatures.add("containsEtAl");
            if (lineInfos[i].text.matches("^(\\p{Lu}\\.\\s*)+\\s+[\\p{Lu}\\p{Ll}]+.*")  // M. I. Jordan
                    || squishedText.matches("^\\p{Lu}[\\p{Lu}\\p{Ll}]+\\,\\p{Lu}[\\.,].*")) {  // Jordan, M. or Jordan,M. or Jordan, M,
                lineInfos[i].presentFeatures.add("beginsCapitalInitials");
                numBeginCapInitials++;
            }

            for (int j = 0; j < keywords.length; j++) {
                if (lineInfos[i].text.matches(".*" + keywords[j] + ".*")) {
                    lineInfos[i].presentFeatures.add("containsKeyword");
                    break;
                }
            }
            for (int j = 0; j < postwords.length; j++) {
                if (lineInfos[i].text.matches(".*" + postwords[j] + ".*")) {
                    lineInfos[i].presentFeatures.add("containsPostword1");
                    break;
                }
            }
            for (int j = 0; j < lowPostwords.length; j++) {
                if (lineInfos[i].text.matches(".*" + lowPostwords[j] + ".*")) {
                    lineInfos[i].presentFeatures.add("containsPostword2");
                    break;
                }
            }
            for (int j = 0; j < months.length; j++) {
                if (lineInfos[i].text.matches(".*" + months[j] + ".*")) {
                    lineInfos[i].presentFeatures.add("containsMonth");
                    break;
                }
            }
        }

//		System.out.println("ppppppppp numPages=" + numPages);

        // Features based on the entire sequence
        // Very long biblio->end section probably means there's an appendix, so
        // there's more chance of encountering spurious reference begin markers.
        // If a paper contains an appendix, assume it cites at least 5 papers
        double threshold = (numPages > 2) ? 4  : 1;
        boolean seqHasBeginBrackets = false;
        boolean seqHasBeginNumberCapital = false;
        int max = 0;

        if (numBeginBrackets > numBeginNumberCapital && numBeginBrackets > numBeginCapInitials) {
            seqHasBeginBrackets = true;
            max = numBeginBrackets;
        }
        else if (numBeginNumberCapital > numBeginBrackets && numBeginNumberCapital > numBeginCapInitials) {
            seqHasBeginNumberCapital = true;
            max = numBeginNumberCapital;
        }
        else
            max = numBeginCapInitials;

        if (max <= threshold)
            return;

        for (int i = 0; i < lineInfos.length; i++) {
            if (seqHasBeginBrackets)
                lineInfos[i].presentFeatures.add("seqHasBeginBrackets");
            else if (seqHasBeginNumberCapital)
                lineInfos[i].presentFeatures.add("seqHasBeginNumberCapital");
            else
                lineInfos[i].presentFeatures.add("seqHasBeginCapInitials");
        }
	}

	private static int specialPunctCounter(String s)
	{
		int count = 0; 
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);

			if (c == ',' || c == '.' || c == ':') 
				count++;
		}
		
		return count;
	}
	
}

class ColumnData
{
    private int leftX;
    private int rightX;

    public int getLeftX() {
        return leftX;
    }

    public void setLeftX(int leftX) {
        this.leftX = leftX;
    }

    public int getRightX() {
        return rightX;
    }

    public void setRightX(int rightX) {
        this.rightX = rightX;
    }

    public int getWidth() {
        return (rightX - leftX);
    }

    @Override
    public boolean equals(Object obj )
    {
        return ((ColumnData)obj).rightX == this.rightX &&
                ((ColumnData)obj).leftX == this.leftX;
    }

    @Override
    public int hashCode()
    {
        return Integer.valueOf(leftX).hashCode();
    }
}