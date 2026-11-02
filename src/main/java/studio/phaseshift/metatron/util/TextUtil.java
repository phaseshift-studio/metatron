/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class TextUtil {

    public enum StopWordSet {
        SMALL(""),
        MEDIUM(""),
        LARGE("terrier_stop_word_list_en.txt"),
        ALL("");

        public final String value;

        StopWordSet(final String value) {
            this.value = value;
        }

    }


    private static final Map<StopWordSet, Set<String>> STOP_WORD_SETS = new ConcurrentHashMap<>();
    private static final Set<String> SMALL_STOP_WORDS = Set.of("able", "about", "above", "abroad", "according", "accordingly",
            "across", "actually", "adj", "after", "afterwards", "again", "against", "ago", "ahead", "ain't", "all", "allow",
            "allows", "almost", "alone", "along", "alongside", "already", "also", "although", "always", "am", "amid", "amidst",
            "among", "amongst", "an", "and", "another", "any", "anybody", "anyhow", "anyone", "anything", "anyway", "anyways",
            "anywhere", "apart", "appear", "appreciate", "appropriate", "are", "aren't", "around", "as", "a's", "aside", "ask",
            "asking", "associated", "at", "available", "away", "awfully", "back", "backward", "backwards", "be", "became", "because",
            "become", "becomes", "becoming", "been", "before", "beforehand", "begin", "behind", "being", "believe", "below", "beside",
            "besides", "best", "better", "between", "beyond", "both", "brief", "but", "by", "came", "can", "cannot", "cant", "can't",
            "caption", "cause", "causes", "certain", "certainly", "changes", "clearly", "c'mon", "co", "co.", "com", "come", "comes",
            "concerning", "consequently", "consider", "considering", "contain", "containing", "contains", "corresponding", "could",
            "couldn't", "course", "c's", "currently", "dare", "daren't", "definitely", "described", "despite", "did", "didn't",
            "different", "directly", "do", "does", "doesn't", "doing", "done", "don't", "down", "downwards", "during", "each", "edu",
            "eg", "eight", "eighty", "either", "else", "elsewhere", "end", "ending", "enough", "entirely", "especially", "et", "etc",
            "even", "ever", "evermore", "every", "everybody", "everyone", "everything", "everywhere", "ex", "exactly", "example", "except",
            "fairly", "far", "farther", "few", "fewer", "fifth", "first", "five", "followed", "following", "follows", "for", "forever",
            "former", "formerly", "forth", "forward", "found", "four", "from", "further", "furthermore", "get", "gets", "getting", "given",
            "gives", "go", "goes", "going", "gone", "got", "gotten", "greetings", "had", "hadn't", "half", "happens", "hardly", "has",
            "hasn't", "have", "haven't", "having", "he", "he'd", "he'll", "hello", "help", "hence", "her", "here", "hereafter", "hereby",
            "herein", "here's", "hereupon", "hers", "herself", "he's", "hi", "him", "himself", "his", "hither", "hopefully", "how",
            "howbeit", "however", "hundred", "i'd", "ie", "if", "ignored", "i'll", "i'm", "immediate", "in", "inasmuch", "inc", "inc.",
            "indeed", "indicate", "indicated", "indicates", "inner", "inside", "insofar", "instead", "into", "inward", "is", "isn't", "it",
            "it'd", "it'll", "its", "it's", "itself", "i've", "just", "k", "keep", "keeps", "kept", "know", "known", "knows", "last",
            "lately", "later", "latter", "latterly", "least", "less", "lest", "let", "let's", "like", "liked", "likely", "likewise",
            "little", "look", "looking", "looks", "low", "lower", "ltd", "made", "mainly", "make", "makes", "many", "may", "maybe",
            "mayn't", "me", "mean", "meantime", "meanwhile", "merely", "might", "mightn't", "mine", "minus", "miss", "more", "moreover",
            "most", "mostly", "mr", "mrs", "much", "must", "mustn't", "my", "myself", "name", "namely", "nd", "near", "nearly", "necessary", "need",
            "needn't", "needs", "neither", "never", "neverf", "neverless", "nevertheless", "new", "next", "nine", "ninety", "no", "nobody", "non",
            "none", "nonetheless", "noone", "no-one", "nor", "normally", "not", "nothing", "notwithstanding", "novel", "now", "nowhere", "obviously",
            "of", "off", "often", "oh", "ok", "okay", "old", "on", "once", "one", "ones", "one's", "only", "onto", "opposite", "or", "other",
            "others", "otherwise", "ought", "oughtn't", "our", "ours", "ourselves", "out", "outside", "over", "overall", "own", "particular",
            "particularly", "past", "per", "perhaps", "placed", "please", "plus", "possible", "presumably", "probably", "provided", "provides",
            "que", "quite", "qv", "rather", "rd", "re", "really", "reasonably", "recent", "recently", "regarding", "regardless", "regards", "relatively",
            "respectively", "right", "round", "said", "same", "saw", "say", "saying", "says", "second", "secondly", "see", "seeing", "seem", "seemed",
            "seeming", "seems", "seen", "self", "selves", "sensible", "sent", "serious", "seriously", "seven", "several", "shall", "shan't", "she", "she'd",
            "she'll", "she's", "should", "shouldn't", "since", "six", "so", "some", "somebody", "someday", "somehow", "someone", "something", "sometime",
            "sometimes", "somewhat", "somewhere", "soon", "sorry", "specified", "specify", "specifying", "still", "sub", "such", "sup", "sure", "take",
            "taken", "taking", "tell", "tends", "th", "than", "thank", "thanks", "thanx", "that", "that'll", "thats", "that's", "that've", "the", "their",
            "theirs", "them", "themselves", "then", "thence", "there", "thereafter", "thereby", "there'd", "therefore", "therein", "there'll", "there're",
            "theres", "there's", "thereupon", "there've", "these", "they", "they'd", "they'll", "they're", "they've", "thing", "things", "think", "third",
            "thirty", "this", "thorough", "thoroughly", "those", "though", "three", "through", "throughout", "thru", "thus", "till", "to", "together", "too",
            "took", "toward", "towards", "tried", "tries", "truly", "try", "trying", "t's", "twice", "two", "un", "under", "underneath", "undoing",
            "unfortunately", "unless", "unlike", "unlikely", "until", "unto", "up", "upon", "upwards", "us", "use", "used", "useful", "uses", "using", "usually",
            "v", "value", "various", "versus", "very", "via", "viz", "vs", "want", "wants", "was", "wasn't", "way", "we", "we'd", "welcome", "well", "we'll",
            "went", "were", "we're", "weren't", "we've", "what", "whatever", "what'll", "what's", "what've", "when", "whence", "whenever", "where", "whereafter",
            "whereas", "whereby", "wherein", "where's", "whereupon", "wherever", "whether", "which", "whichever", "while", "whilst", "whither", "who", "who'd",
            "whoever", "whole", "who'll", "whom", "whomever", "who's", "whose", "why", "will", "willing", "wish", "with", "within", "without", "wonder", "won't",
            "would", "wouldn't", "yes", "yet", "you", "you'd", "you'll", "your", "you're", "yours", "yourself", "yourselves", "you've", "zero", "a", "how's",
            "i", "when's", "why's", "b", "c", "d", "e", "f", "g", "h", "j", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "uucp", "w", "x", "y", "z", "I",
            "www", "amount", "bill", "bottom", "call", "computer", "con", "couldnt", "cry", "de", "describe", "detail", "due", "eleven", "empty", "fifteen",
            "fifty", "fill", "find", "fire", "forty", "front", "full", "give", "hasnt", "herse", "himse", "interest", "itse”", "mill", "move", "myse”", "part",
            "put", "show", "side", "sincere", "sixty", "system", "ten", "thick", "thin", "top", "twelve", "twenty", "abst", "accordance", "act", "added",
            "adopted", "affected", "affecting", "affects", "ah", "announce", "anymore", "apparently", "approximately", "aren", "arent", "arise", "auth",
            "beginning", "beginnings", "begins", "biol", "briefly", "ca", "date", "ed", "effect", "et-al", "ff", "fix", "gave", "giving", "heres", "hes",
            "hid", "home", "id", "im", "immediately", "importance", "important", "index", "information", "invention", "itd", "keys", "kg", "km", "largely",
            "lets", "line", "'ll", "means", "mg", "million", "ml", "mug", "na", "nay", "necessarily", "nos", "noted", "obtain", "obtained", "omitted", "ord",
            "owing", "page", "pages", "poorly", "possibly", "potentially", "pp", "predominantly", "present", "previously", "primarily", "promptly", "proud",
            "quickly", "ran", "readily", "ref", "refs", "related", "research", "resulted", "resulting", "results", "run", "sec", "section", "shed", "shes",
            "showed", "shown", "showns", "shows", "significant", "significantly", "similar", "similarly", "slightly", "somethan", "specifically", "state",
            "states", "stop", "strongly", "substantially", "successfully", "sufficiently", "suggest", "thered", "thereof", "therere", "thereto", "theyd",
            "theyre", "thou", "thoughh", "thousand", "throug", "til", "tip", "ts", "ups", "usefully", "usefulness", "'ve", "vol", "vols", "wed", "whats",
            "wheres", "whim", "whod", "whos", "widely", "words", "world", "youd", "youre");

    private static final String[] MEDIUM_STOP_WORDS = {
            "...", ".", "..",
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't", "as", "at",
            "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
            "can't", "cannot", "could", "couldn't",
            "did", "didn't", "do", "does", "doesn't", "doing", "don't", "down", "during",
            "each",
            "few", "for", "from", "further",
            "had", "hadn't", "has", "hasn't", "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself", "him", "himself", "his", "how", "how's",
            "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't", "it", "it's", "its", "itself",
            "let's",
            "me", "more", "most", "mustn't", "my", "myself",
            "no", "nor", "not",
            "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out", "over", "own",
            "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so", "some", "such",
            "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then", "there", "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those", "through", "to", "too",
            "under", "until", "up",
            "very",
            "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were", "weren't", "what", "what's", "when", "when's", "where", "where's", "which", "while", "who", "who's", "whom", "why", "why's", "with", "won't", "would", "wouldn't",
            "you", "you'd", "you'll", "you're", "you've", "your", "yours", "yourself", "yourselves",
            // Extended list including less common forms and variations
            "able", "according", "accordingly", "across", "actually", "afterwards", "ain", "allow", "allows", "almost", "alone", "along", "already", "also", "although", "always", "among", "amongst", "another", "anybody", "anyhow", "anyone", "anything", "anyway", "anyways", "anywhere", "apart", "appear", "appreciate", "appropriate", "around", "aside", "ask", "asking", "associated", "available", "away", "awfully",
            "back", "became", "become", "becomes", "becoming", "beforehand", "behind", "believe", "beside", "besides", "best", "better", "beyond", "brief",
            "came", "cause", "causes", "certain", "certainly", "changes", "clearly", "co", "com", "come", "comes", "concerning", "consequently", "consider", "considering", "contain", "containing", "contains", "corresponding", "course", "currently",
            "definitely", "described", "despite", "did", "different", "done", "downwards", "due",
            "edu", "eg", "eight", "either", "else", "elsewhere", "enough", "entirely", "especially", "et", "etc", "even", "ever", "every", "everybody", "everyone", "everything", "everywhere", "ex", "exactly", "example", "except",
            "far", "ff", "fifth", "first", "five", "followed", "following", "follows", "former", "formerly", "forth", "four", "front", "full", "furthermore",
            "get", "gets", "getting", "given", "gives", "go", "goes", "going", "gone", "got", "gotten", "greetings",
            "happens", "hardly", "hence", "help", "hereafter", "hereby", "herein", "hereupon", "hi", "hopefully", "howbeit", "however",
            "ie", "ignored", "immediate", "inasmuch", "inc", "indeed", "indicate", "indicated", "indicates", "inner", "insofar", "instead", "inward",
            "just",
            "keep", "keeps", "kept", "know", "known", "knows",
            "last", "lately", "later", "latter", "latterly", "least", "less", "lest", "let", "like", "liked", "likely", "little", "look", "looking", "looks", "ltd",
            "mainly", "many", "may", "maybe", "mean", "meanwhile", "merely", "might", "moreover", "mostly", "much", "must",
            "name", "namely", "nd", "near", "nearly", "necessary", "need", "needs", "neither", "never", "nevertheless", "new", "next", "nine", "nobody", "non", "none", "noone", "normally", "nothing", "novel", "nowhere",
            "obviously", "oh", "ok", "okay", "old", "onto", "others", "otherwise", "outside", "overall", "own",
            "particular", "particularly", "per", "perhaps", "placed", "please", "plus", "possible", "presumably", "probably", "provides",
            "que", "quite", "qv",
            "rather", "rd", "re", "really", "reasonably", "regarding", "regardless", "regards", "relatively", "respectively", "right",
            "said", "saw", "say", "saying", "says", "second", "secondly", "see", "seeing", "seem", "seemed", "seeming", "seems", "seen", "self", "selves", "sensible", "sent", "serious", "seriously", "seven", "several", "shall", "side", "since", "six", "slightly", "somebody", "somehow", "someone", "something", "sometime", "sometimes", "somewhat", "somewhere", "soon", "sorry", "specified", "specify", "specifying", "state", "states", "still", "sub", "sure",
            "take", "taken", "tell", "tends", "th", "thank", "thanks", "thanx", "third", "thorough", "thoroughly", "thought", "thoughts", "thru", "thus", "together", "took", "toward", "towards", "tried", "tries", "truly", "try", "trying", "twice", "two",
            "un", "underneath", "unfortunately", "unless", "unlikely", "unto", "upon", "use", "used", "useful", "uses", "using", "usually",
            "value", "various", "via", "viz", "vs",
            "want", "wants", "way", "welcome", "went", "well", "whatever", "whence", "whenever", "whereafter", "whereas", "whereby", "wherein", "whereupon", "wherever", "whether", "whilst", "whither", "whoever", "whole", "whose", "willing", "wish", "wonder", "wrote",
            "yes", "yet",
            "zero"
    };

    private TextUtil() {
        // do nothing
    }

    public static String stripStopwords(final StopWordSet set, final String text) {
        final Set<String> stopWords = getStopWords(set);
        final String[] words = text.split("\\s+");
        final StringBuilder filteredText = new StringBuilder();
        for (String word : words) {
            // Clean punctuation and normalize case
            String cleanWord = word.replaceAll("[^a-zA-Z]", "").toLowerCase();
            if (!stopWords.contains(cleanWord)) {
                filteredText.append(cleanWord).append(" ");
            }
        }
        return filteredText.toString().trim();
    }

    public static Set<String> getStopWords(final StopWordSet set) {
        if (STOP_WORD_SETS.containsKey(set))
            return STOP_WORD_SETS.get(set);
        if (set.equals(StopWordSet.ALL)) {
            final Set<String> allStopWords = new HashSet<>();
            for (final StopWordSet s : StopWordSet.values()) {
                if (!s.equals(StopWordSet.ALL)) {
                    allStopWords.addAll(getStopWords(s));
                }
            }
            STOP_WORD_SETS.put(StopWordSet.ALL, allStopWords);
            return allStopWords;
        }
        if (set.equals(StopWordSet.LARGE)) {
            final InputStream in = TextUtil.class.getResourceAsStream(StopWordSet.LARGE.value);
            if (in == null) {
                throw MTronException.of(
                        StopWordSet.LARGE.value +
                                " not found on classpath — "
                                + "ensure src/main/resources is on the classpath and " +
                                StopWordSet.LARGE.value +
                                " is included in the build");
            }
            final Set<String> words = new HashSet<>();
            try (final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    words.add(line.trim().toLowerCase());
                }
            } catch (final IOException e) {
                throw MTronException.of("failed to read %s: %s", StopWordSet.LARGE.value, e.getMessage());
            }
            STOP_WORD_SETS.put(StopWordSet.LARGE, words);
            return words;
        } else if (set.equals(StopWordSet.MEDIUM)) {
            final Set<String> words = new HashSet<>(Arrays.asList(MEDIUM_STOP_WORDS));
            STOP_WORD_SETS.put(StopWordSet.MEDIUM, words);
            return words;
        } else if (set.equals(StopWordSet.SMALL)) {
            STOP_WORD_SETS.put(StopWordSet.SMALL, SMALL_STOP_WORDS);
            return SMALL_STOP_WORDS;
        } else {
            throw MTronException.of("unknown stop word set: %s", set);
        }
    }
}
