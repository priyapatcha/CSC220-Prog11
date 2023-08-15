package prog11;

import prog05.ArrayQueue;
import javax.swing.*;
import java.util.*;




public class NotGPT implements SearchEngine {

    Disk pageDisk = new Disk();
    Disk wordDisk = new Disk();
    Map<String, Long> url2index = new TreeMap<>();
    Map<String, Long> word2index = new HashMap<>();


    @Override
    public void collect(Browser browser, List<String> startingURLs) {
        Queue<Long> queue = new ArrayDeque<>();

        for (String url : startingURLs) {
            if (!url2index.containsKey(url)) {
                Long index = indexPage(url);
                queue.add(index);
            }
        }
        while (!queue.isEmpty()) {
            Long currentIndex = queue.poll();
            InfoFile pageFile = pageDisk.get(currentIndex);

            if (browser.loadPage(pageFile.data)) {
                List<String> urls = browser.getURLs();
                Set<String> urlsOnPage = new HashSet<String>();
                for (String url : browser.getURLs()) {
                    if (!urlsOnPage.contains(url)) {
                        urlsOnPage.add(url);
                        Long indx2 = url2index.get(url);
                        if (indx2 == null) {
                            indx2 = indexPage(url);
                            queue.offer(indx2);
                        }
                        pageFile.indices.add(indx2);
                    }

                }
                pageDisk.put(currentIndex, pageFile);

                List<String> words = browser.getWords();
                Set<String> wordsOnPage = new HashSet<String>();

                for (String word : words) {
                    if (!wordsOnPage.contains(word)) {
                        wordsOnPage.add(word);
                        Long indexOfWord = word2index.get(word);
                        if (indexOfWord == null)
                            indexOfWord = indexWord(word);
                        InfoFile wordFile = wordDisk.get(indexOfWord);
                        wordFile.indices.add(currentIndex);
                        wordDisk.put(indexOfWord, wordFile);
                    }
                }
            }
        }


    }


    @Override
    public void rank(boolean fast) {
        for (Map.Entry<Long, InfoFile> entry : pageDisk.entrySet()) {
            long index = entry.getKey();
            InfoFile file = entry.getValue();
            file.priority = 1.0;
            file.tempPriority = 0.0;
        }
        double count = 0.0;

        for (Map.Entry<Long, InfoFile> entry : pageDisk.entrySet()) {
            InfoFile file = entry.getValue();
            if (file.indices.size() == 0) {
                ++count;
            }
        }

        double defaultPriority = 1 * count / pageDisk.size();

        if (!fast) {
            for (int i = 0; i < 20; i++)
                rankSlow(defaultPriority);
        } else {
            for (int i = 0; i < 20; i++)
                rankFast(defaultPriority);
        }

    }

    @Override
    public String[] search(List<String> searchWords, int numResults) {
        Iterator<Long>[] wordFileIterators = (Iterator<Long>[]) new Iterator[searchWords.size()];
        long[] currentPageIndexes = new long[searchWords.size()];

        // Matching pages with the least popular page on the top of the queue.
        PriorityQueue<Long> bestPageIndexes = new PriorityQueue<>(new PageComparator());
        PageComparator pageComparator = new PageComparator();

        for (int i = 0; i < searchWords.size(); i++) {
            String word = searchWords.get(i);
            Long index = word2index.get(word);
            InfoFile wordFile = wordDisk.get(index);

            List<Long> indices = wordFile.indices;
            wordFileIterators[i] = indices.iterator();
        }

        while (getNextPageIndexes(currentPageIndexes, wordFileIterators)) {
            if (allEqual(currentPageIndexes)) {
                if (bestPageIndexes.size() < numResults) {
                    bestPageIndexes.offer(currentPageIndexes[0]);
                } else if (pageComparator.compare(currentPageIndexes[0], bestPageIndexes.peek()) > 0) {
                    bestPageIndexes.poll();
                    bestPageIndexes.offer(currentPageIndexes[0]);
                }
                Long index = currentPageIndexes[0];
                InfoFile pageFile = pageDisk.get(index);
                //String url = pageFile.data;
                //System.out.println("Match found: " + url);
                System.out.println(pageFile.data);

            }
        }
        String[] results = new String[bestPageIndexes.size()];
        for (int i = results.length - 1; i >= 0; i--) {
            results[i] = pageDisk.get(bestPageIndexes.poll()).data;
        }
        //return new String[0];
        return results;
    }

    private boolean allEqual(long[] array) {
        for (int i = 1; i < array.length; i++) {
            if (array[i] != array[i - 1]) {
                return false;
            }
        }
        return true;
    }

    private long getLargest(long[] array) {
        long largest = array[0];

        for (int i = 0; i < array.length; i++) {
            if (array[i] > largest) {
                largest = array[i];
            }
        }
        return largest;
    }

    private boolean getNextPageIndexes(long[] currentPageIndexes, Iterator<Long>[] wordFileIterators) {
        if (allEqual(currentPageIndexes)) {
            for (int i = 0; i < wordFileIterators.length; i++) {
                if (!wordFileIterators[i].hasNext()) {
                    return false;
                }
                currentPageIndexes[i] = wordFileIterators[i].next();
            }
        } else {
            long largest = getLargest(currentPageIndexes);

            for (int i = 0; i < wordFileIterators.length; i++) {
                if (currentPageIndexes[i] != largest) {
                    if (!wordFileIterators[i].hasNext()) {
                        return false;
                    }
                    currentPageIndexes[i] = wordFileIterators[i].next();
                }
            }
        }
        return true;
    }

    private Long indexPage(String url) {
        Long index = pageDisk.newFile();
        InfoFile newInfoFile = new InfoFile(url);
        pageDisk.put(index, newInfoFile);

        url2index.put(url, index);
        System.out.println("indexing page " + index + " " + newInfoFile.toString());

        return index;

    }

    private Long indexWord(String word) {
        Long index = wordDisk.newFile();
        InfoFile newInfoFile = new InfoFile(word);
        wordDisk.put(index, newInfoFile);

        word2index.put(word, index);
        System.out.println("indexing word " + index + " " + newInfoFile.toString());

        return index;
    }

    public void rankSlow(double defaultPriority) {
        for (Map.Entry<Long, InfoFile> entry : pageDisk.entrySet()) {
            long index = entry.getKey();
            InfoFile file = entry.getValue();
            double priorityPerIndex = file.priority / file.indices.size();

            //for (Map.Entry<Long, InfoFile> indices : pageDisk.entrySet()) {

            for (long i : file.indices)
                pageDisk.get(i).tempPriority += priorityPerIndex;
        }

        for (Map.Entry<Long, InfoFile> entry : pageDisk.entrySet()) {
            long index = entry.getKey();
            InfoFile file = entry.getValue();
            file.priority = file.tempPriority + defaultPriority;
            file.tempPriority = 0.0;
        }


    }

    public void rankFast(double defaultPriority) {
        List<Vote> voteArrayList = new ArrayList<>();

        for (Map.Entry<Long, InfoFile> entry : pageDisk.entrySet()) {
            InfoFile file = entry.getValue();

            for (Long index : file.indices) {
                Vote vote = new Vote(index, file.priority / file.indices.size());
                voteArrayList.add(vote);
            }
        }

        Collections.sort(voteArrayList);
        Iterator<Vote> iterator = voteArrayList.iterator();
        Vote currentVote = iterator.next();

        for (Map.Entry<Long, InfoFile> entry : pageDisk.entrySet()) {
            long index = entry.getKey();
            InfoFile file = entry.getValue();
            file.priority = defaultPriority;


            while (currentVote != null && currentVote.index == index) {
                file.priority += currentVote.vote;
                if (iterator.hasNext())
                    currentVote = iterator.next();
                else break;
            }
//            file.priority = defaultPriority + file.tempPriority;
//            file.tempPriority = 0;
        }
    }

    class PageComparator implements Comparator<Long> {

        @Override
        public int compare(Long pageIndex1, Long pageIndex2) {
            double diff = pageDisk.get(pageIndex1).priority - pageDisk.get(pageIndex2).priority;
            if (diff < 0) {
                return -1;
            }
            else if (diff > 0) {
                return 1;
            }
            else {
                return 0;
            }
        }

    }
}

    class Vote implements Comparable<Vote> {
        Long index;
        double vote;
        Vote (Long index, double vote) {
                this.index = index;
                this.vote = vote;
        }


        @Override
        public int compareTo(Vote o) {
            return (int) (index - o.index);
        }



    }








