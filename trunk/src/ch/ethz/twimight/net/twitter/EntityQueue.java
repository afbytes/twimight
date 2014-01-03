package ch.ethz.twimight.net.twitter;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

import twitter4j.TweetEntity;

/**
 * A specialized PriorityQueue that serves the added entities sorted by their
 * appearance in the tweet. This is useful for replacing the entity text without
 * affecting the indexes of the following entities.
 * 
 * @author msteven
 * 
 */
public class EntityQueue extends PriorityQueue<TweetEntity> {

	private static final long serialVersionUID = 4428463091015289900L;

	public EntityQueue() {
		super(1, new Comparator<TweetEntity>() {
			@Override
			public int compare(TweetEntity lhs, TweetEntity rhs) {
				return rhs.getStart() - lhs.getStart();
			}
		});

	}

	/**
	 * Convenience constructor for directly initializing the queue with the
	 * desired entries.
	 * 
	 * @param entityArrays arrays of entities to add
	 */
	public EntityQueue(TweetEntity[]... entityArrays) {
		this();
		for (TweetEntity[] entityArray : entityArrays) {
			addAll(entityArray);
		}
	}

	/**
	 * Convenience method for adding arrays.
	 * 
	 * @param entities
	 *            an array of entities
	 * @return true if this Collection is modified, false otherwise.
	 */
	public boolean addAll(TweetEntity[] entities) {
		boolean result = false;
		if(entities!=null){
			result = addAll(Arrays.asList(entities));
		}
		return result;
	}

}
