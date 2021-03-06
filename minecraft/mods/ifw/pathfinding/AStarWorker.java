package mods.ifw.pathfinding;

import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * Runnable worker class for finding an AstarPath
 * is prone to crashes when no path can be found.
 *
 * @author AtomicStryker
 */

public class AStarWorker extends Thread {
    /**
     * How long a pathfinding thread is allowed to run before the path is deemed
     * impossible to calculate. A reasonably difficult path will always be finished
     * in under one millisecond. Value in milliseconds. 500L default.
     */
    private final long SEARCH_TIME_LIMIT = 500L;

    /**
     * How many cubes will the worker check for a path before giving up
     */
    protected AStarPathPlanner boss;

    public final ArrayList<AStarNode> closedNodes;
    private AStarNode startNode;
    protected AStarNode targetNode;
    private boolean allowDropping;
    protected World worldObj;
    protected long timeLimit;
    private final PriorityQueue<AStarNode> queue;

    public AStarWorker(AStarPathPlanner creator) {
        boss = creator;
        closedNodes = new ArrayList<AStarNode>();
        queue = new PriorityQueue<AStarNode>(500);
    }

    @Override
    public void run() {
        timeLimit = System.currentTimeMillis() + SEARCH_TIME_LIMIT + AStarStatic.getDistanceBetween(startNode.x, startNode.y, startNode.z, targetNode.x, targetNode.y, targetNode.z);
        ArrayList<AStarNode> result = null;
        long time = System.nanoTime();
        result = getPath(startNode, targetNode, allowDropping);
        time = System.nanoTime() - time;

        if (result == null) {
            //System.out.println(getClass()+" finished. No path.");
            System.out.println("Total time in Seconds: " + time / 1000000000D);
            boss.onNoPathAvailable();
        } else {
            //System.out.println(getClass()+" finished. Path Length: "+result.size());
            System.out.println("Total time in Seconds: " + time / 1000000000D);
            boss.onFoundPath(this, result);
        }
    }

    protected boolean shouldInterrupt() {
        return System.currentTimeMillis() > timeLimit;
    }

    /**
     * Setup some pointers for the seaching run
     *
     * @param winput World to search in
     * @param start  Starting Node
     * @param end    Target Node of the Path
     * @param mode   true if dropping more than 1 Block is allowed
     */
    public void setup(World winput, AStarNode start, AStarNode end, boolean mode) {
        worldObj = winput;
        startNode = start;
        targetNode = end;
        allowDropping = mode;
        //System.out.println("Start Node: "+start.x+", "+start.y+", "+start.z);
        //System.out.println("Target Node: "+end.x+", "+end.y+", "+end.z);
    }

    public ArrayList<AStarNode> getPath(AStarNode start, AStarNode end, boolean searchMode) {
        queue.offer(start);
        targetNode = end;

        AStarNode current = start;

        while (!current.equals(end)) {
            closedNodes.add(queue.poll());

            checkPossibleLadder(current);
            getNextCandidates(current, searchMode);

            if (queue.isEmpty() || shouldInterrupt()) {
                //System.out.println("Path search aborted, interrupted: "+shouldInterrupt());
                return null;
            }
            current = queue.peek();
            //System.out.println("current Node is now "+current.x+", "+current.y+", "+current.z+" of cost "+current.getF());
        }

        ArrayList<AStarNode> foundpath = new ArrayList<AStarNode>();
        foundpath.add(current);
        while (current != start) {
            foundpath.add(current.parent);
            current = current.parent;
        }

        //System.out.println("Path search success, visited "+checkedCubes+" Nodes, pathpoints: "+foundpath.size()+", abs. distance "+AStarStatic.getDistanceBetweenNodes(start, end));
        return foundpath;
    }

    private void addToBinaryHeap(AStarNode input) {
        queue.offer(input);
    }

    /**
     * Checks a Node for being a Ladder and adds it's up- and downward Blocks to
     * the Heap if it is.
     *
     * @param node Node being checked
     */
    private void checkPossibleLadder(AStarNode node) {
        int x = node.x;
        int y = node.y;
        int z = node.z;

        if (AStarStatic.isLadder(worldObj, worldObj.getBlockId(x, y, z), x, y, z)) {
            AStarNode ladder = null;
            if (AStarStatic.isLadder(worldObj, worldObj.getBlockId(x, y + 1, z), x, y + 1, z)) ;
            {
                ladder = new AStarNode(x, y + 1, z, node, targetNode);
                if (!tryToUpdateExistingHeapNode(node, ladder)) {
                    addToBinaryHeap(ladder);
                }
            }
            if (AStarStatic.isLadder(worldObj, worldObj.getBlockId(x, y - 1, z), x, y - 1, z)) ;
            {
                ladder = new AStarNode(x, y - 1, z, node, targetNode);
                if (!tryToUpdateExistingHeapNode(node, ladder)) {
                    addToBinaryHeap(ladder);
                }
            }
        }
    }

    /**
     * Expands a Node to find new Candidates
     *
     * @param parent          Node being expanded
     * @param droppingAllowed if true, will consider Blocks around more than 1 lower
     */
    public void getNextCandidates(AStarNode parent, boolean droppingAllowed) {
        int x = parent.x;
        int y = parent.y;
        int z = parent.z;
        int[][] c = droppingAllowed ? AStarStatic.candidates_allowdrops : AStarStatic.candidates;

        AStarNode check;
        for (int i = 0; i < c.length; i++) {
            check = new AStarNode(x + c[i][0], y + c[i][1], z + c[i][2], parent, targetNode);

            boolean found = false;
            Iterator<AStarNode> iter = closedNodes.iterator();
            while (iter.hasNext()) {
                AStarNode toUpdate = iter.next();
                if (check.equals(toUpdate)) {
                    toUpdate.setG(check.getG() + getCostNodeToNode(toUpdate, check), parent);
                    found = true;
                    break;
                }
            }

            if (!found && !tryToUpdateExistingHeapNode(parent, check)) {
                if (AStarStatic.isViable(worldObj, check, c[i][1])) {
                    addToBinaryHeap(check);
                }
            }
        }
    }

    /**
     * Calculates linear movement cost between two AStarNodes
     *
     * @param a Node
     * @param b Node
     * @return 1 for every adjacent Block (no diagonals!) you have to traverse to get from a to b
     */
    private int getCostNodeToNode(AStarNode a, AStarNode b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z);
    }

    /**
     * Searches the Heap for an existing equal Node and updates it's distance and
     * parent Node fields if it finds one.
     *
     * @param parent     Parent Node to update the field with
     * @param checkedOne Node to find an equal of
     * @return true if a Node was found, false otherwise
     */
    private boolean tryToUpdateExistingHeapNode(AStarNode parent, AStarNode checkedOne) {
        Iterator<AStarNode> iter = queue.iterator();
        AStarNode itNode;
        while (iter.hasNext()) {
            itNode = iter.next();
            if (itNode.equals(checkedOne)) {
                itNode.setG(checkedOne.getG(), parent);
                return true;
            }
        }
        return false;
    }
}