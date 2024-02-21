package bguspl.set.ex;

import java.util.concurrent.ArrayBlockingQueue;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The dealer object.
     */
    private final Dealer dealer;


    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /** 
     * 
     * @param env
     * @param dealer
     * @param table
     * @param id
     * @param human
     */
    private ArrayBlockingQueue<Integer> keyPresses;
    //private int counter=0;
    private boolean frosen;
    private boolean pointFreze;
    protected Object lockPress;
    protected Object playerLock;
    //final private Object LockQueue = new Object();


    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        playerThread = Thread.currentThread();
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        terminate=false;
        frosen=false;
        pointFreze=false;
        this.keyPresses = new ArrayBlockingQueue<>(3);
        lockPress=new Object();
        playerLock=new Object();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */

                 //while("size precces setsize in dealer is up when i putted token" || || keyPresses.size()==0)
                //Thread.currentThread().wait();
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) 
        {   
            while(keyPresses.isEmpty())
            {
                try 
                {
                    synchronized(lockPress)
                    {
                        lockPress.wait();
                    }
                } 
                catch (InterruptedException ignored) {}
            }  
            
            act();            
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    
    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) 
            {
                // TODO implement player key press simulator
                try {
                    synchronized (playerLock) { playerLock.wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate=true;
        // TODO implement
        
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public synchronized void keyPressed(int slot) 
    {
        try 
        {
            synchronized(lockPress)
            {
            if(!pointFreze && !frosen){
                keyPresses.put(slot);  
                lockPress.notifyAll();                
            }
        }
        } 
        catch (InterruptedException e) {}
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() 
    {
        // TODO implement

        //    int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        pointFreze=true;
    }

    /**
     * Penalize a player and perform other related actions.
     */
     public void penalty() 
    {
        frosen = true;
    }
    

    public int score() {
        return score;
    }    
    private void act()
    {
        
        synchronized (playerLock) {
                try
                {
                    int slot = keyPresses.take();
                    if (slot >= 0 && slot < table.countCards()) 
                    {
                        if(!(table.removeToken(this.id, slot)))
                        {
                            table.placeToken(this.id, slot);
                            if(table.CheckExistPlayerSet(id))
                            {
                                
                                dealer.Addplayer(this.id);
                                try {
                                    System.out.println("lock");
                                    playerLock.wait();
                                } catch (InterruptedException e) {}

                                if(frosen)
                                {
                                    try 
                                    {
                                        long freezTime = env.config.penaltyFreezeMillis;
                                        env.ui.setFreeze(id, freezTime ); //replace, magic number
                                        while(freezTime>0)
                                        {

                                            env.ui.setFreeze(id, freezTime );
                                            //player thread
                                            Thread.sleep(1000-env.config.penaltyFreezeMillis%1000);
                                            freezTime=freezTime-1000;
                                        }
                                        env.ui.setFreeze(id, 0);
                                        frosen = false;
                                        } 
                                        catch (InterruptedException ignored) {
                                    }
                
                                }

                                if(pointFreze)
                                { 
                                    try 
                                    {
                                        long freezTime = env.config.pointFreezeMillis;
                                        env.ui.setFreeze(id, freezTime ); //replace, magic number
                                        while(freezTime>0)
                                        {

                                            env.ui.setFreeze(id, freezTime );
                                            //player thread
                                            Thread.sleep(1000-env.config.pointFreezeMillis%1000);
                                            freezTime=freezTime-1000;
                                        }
                                        env.ui.setFreeze(id, 0);
                                        pointFreze = false;
                                        } 
                                        catch (InterruptedException ignored) {
                                    }
                                   /* 
                                   try
                                    {
                                        env.ui.setFreeze(id, env.config.pointFreezeMillis);
                                        Thread.sleep(env.config.pointFreezeMillis);
                                    }
                                    catch(InterruptedException e)
                                    {
                                    }
                                    pointFreze=false;*/
                                }
                            }
                        }
                    }
                }
                catch(InterruptedException e)
                {
                }
        }
    }
    
    
}
