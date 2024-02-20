package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private ArrayBlockingQueue <Integer> playersWithCardsToRemove;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        terminate=false; //here i changed
        playersWithCardsToRemove = new ArrayBlockingQueue<>(env.config.players);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players) 
        {
            ThreadLogger playerThread = new ThreadLogger(player, "player " + player.id, env.logger);
            playerThread.startWithLog();
        }
        while (!shouldFinish()) 
        {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) 
        {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        if(deck.size()==0 || table.countCards()==0)
        {
            for(Player player:players)
            {
                player.terminate();
            }
            terminate=true;
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() 
    {
        // TODO implement
        try
        {
            int id = playersWithCardsToRemove.take();
            int[] cards=table.getCardsPlayer(id);
            table.removeCard(cards[0]);
            table.removeCard(cards[1]);
            table.removeCard(cards[2]);
        }
        catch(InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() 
    {
        // TODO implement
        Collections.shuffle(deck);
        List<Integer> places = IntStream.range(0, env.config.tableSize).boxed().collect(Collectors.toList());
        Collections.shuffle(places);
        for(int i=0;i<places.size();i++)
        {
            if(deck.size()>0 && table.slotToCard[places.get(i)] == null)
            {
                table.placeCard(deck.get(0), places.get(i));
                deck.remove(0);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() 
    {
        synchronized (this) 
        {
            try 
            {
                // Calculate the remaining time until reshuffle
                long timeNoWarning = reshuffleTime - System.currentTimeMillis() - env.config.turnTimeoutWarningMillis;
                while (timeNoWarning > 0) 
                {
                    wait(1000); //magic number
                    timeNoWarning -= 1000; //magic number
                    updateTimerDisplay(false);
                    checkChosenSets();
                }
                
            } 
            catch (InterruptedException ignored) {}
        }
        // TODO implement
    }

    private void checkChosenSets() 
    {
        for (Player player : players) 
        {
            int[] chosen = new int[3];
            int counter = 0;
            boolean ans = false;
            for(int i = 0; i < env.config.tableSize; i++)
            {
                if(table.playerTokens[player.id][i] == true)
                {
                    chosen[counter] = table.slotToCard[i];
                    counter++;
                }
            }
            if(counter == 3) // maybe not needed
            {
                ans = env.util.testSet(chosen);
            }
            if(ans)
            {
                player.point();
                env.ui.setScore(player.id, player.score());
                try{
                playersWithCardsToRemove.put(player.id);
                }
                catch(InterruptedException e)
                {
                    e.printStackTrace();
                }
                removeCardsFromTable();
            }
            else
            {
                player.penalty();
            }
            synchronized(player)
            {
                player.notifyAll();
            }
        }    
    }

    public void checkWhenNotified(int id)
    {
        synchronized (this) 
        {
            try 
            {
                notifyAll();
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if(reset)
        {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }
        env.ui.setCountdown(reshuffleTime, reshuffleTime-System.currentTimeMillis() < env.config.turnTimeoutWarningMillis);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() 
    {
        // TODO implement
        for(int i=0;i<env.config.tableSize;i++)
        {
            if(table.slotToCard[i]!=null)
            {
                deck.add(table.slotToCard[i]);
                table.removeCard(i);
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int max = 0;
        int winnersounter = 0;
        for(Player player:players) //calc max
        {
            if(player.score()>max)
            {
                max = player.score();
            }
        }
        for(Player player:players) //calc winners amount
        {
            if(player.score()==max)
            {
                winnersounter++;
            }
        }
        int[] winners= new int[winnersounter];
        for(int i = 0; i < players.length; i++) //add winners' id
        {
            if(players[i].score()==max)
            {
                winners[i] = players[i].id;
            }
        }
        env.ui.announceWinner(winners);
    }
}

