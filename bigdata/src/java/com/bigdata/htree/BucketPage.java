package com.bigdata.htree;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.log4j.Level;

import com.bigdata.btree.BytesUtil;
import com.bigdata.btree.IRangeQuery;
import com.bigdata.btree.ITuple;
import com.bigdata.btree.ITupleIterator;
import com.bigdata.btree.Leaf;
import com.bigdata.btree.Node;
import com.bigdata.btree.data.DefaultLeafCoder;
import com.bigdata.btree.data.ILeafData;
import com.bigdata.btree.raba.IRaba;
import com.bigdata.htree.raba.MutableKeyBuffer;
import com.bigdata.htree.raba.MutableValueBuffer;
import com.bigdata.io.AbstractFixedByteArrayBuffer;
import com.bigdata.rawstore.IRawStore;

import cutthecrap.utils.striterators.EmptyIterator;
import cutthecrap.utils.striterators.SingleValueIterator;

/**
 * An {@link HTree} bucket page (leaf). The bucket page is comprised of one or
 * more buddy hash buckets. The #of buddy hash buckets is determined by the
 * address bits of the hash tree and the global depth of the bucket page.
 * <p>
 * The entire bucket page is logically a fixed size array of tuples. The
 * individual buddy hash buckets are simply offsets into that logical tuple
 * array. While inserts of distinct keys drive splits, inserts of identical keys
 * do not. Therefore, this simple logical picture is upset by the need for a
 * bucket to hold an arbitrary number of tuples having the same key.
 * <p>
 * Each tuple is represented by a key, a value, and various metadata bits using
 * the {@link ILeafData} API. The tuple keys are always inline within the page
 * and are often 32-bit integers. The tuple values may be either "raw records"
 * on the backing {@link IRawStore} or inline within the page.
 * 
 * FIXME Add support for raw records (copy from BTree's Leaf class).
 * 
 * TODO One way to tradeoff the simplicity of a local tuple array with the
 * requirement to hold an arbitrary number of duplicate keys within a bucket is
 * to split the bucket if it becomes full regardless of whether or not there are
 * duplicate keys.
 * <p>
 * Splitting a bucket doubles its size which causes a new bucket page to be
 * allocated to store 1/2 of the data. If the keys can be differentiated by
 * increasing the local depth, then this is the normal case and the tuples are
 * just redistributed among buddy buckets on the original bucket page and the
 * new bucket page. If the keys are identical but we force a split anyway, then
 * we will still have fewer buckets on the original page and they will be twice
 * as large. The duplicate keys will all wind up in the same buddy bucket after
 * the split, but at least the buddy bucket is 2x larger. This process can
 * continue until there is only a single buddy bucket on the page (the global
 * depth of the parent is the same as the global depth of the buddy bucket). At
 * that point, a "full" page can simply "grow" by permitting more and more
 * tuples into the page (as long as those tuples have the same key). We could
 * also chain overflow pages at that point - it all amounts to the same thing.
 * An attempt to insert a tuple having a different key into a page in which all
 * keys are known to be identical will immediately trigger a split. In this case
 * we could avoid some effort if we modified the directory structure to impose a
 * split since we already know that we have only two distinct keys (the one
 * found in all tuples of the bucket and the new key). When a bucket page
 * reaches this condition of containing only duplicate keys we could of course
 * compress the keys enormously since they are all the same.
 * <p>
 * This works well when we only store the address of the objects in the bucket
 * (rather than the objects themselves, e.g., raw records mode) and choose the
 * address bits based on the expected size of a tuple record. However, we can
 * also accommodate tuples with varying size (think binding sets) with in page
 * locality if split the buddy bucket with the most bytes when an insert into
 * the page would exceed the target page size. This looks pretty much like the
 * case above, except that we split buddy buckets based on not only whether
 * their alloted #of slots are filled by tuples but also based on the data on
 * the page.
 * 
 * TODO Delete markers will also require some thought. Unless we can purge them
 * out at the tx commit point, we can wind up with a full bucket page consisting
 * of a single buddy bucket filled with deleted tuples all having the same key.
 */
class BucketPage extends AbstractPage implements ILeafData { // TODO IBucketData

	/**
	 * The data record. {@link MutableBucketData} is used for all mutation
	 * operations. {@link ReadOnlyLeafData} is used when the {@link Leaf} is
	 * made persistent. A read-only data record is automatically converted into
	 * a {@link MutableBucketData} record when a mutation operation is
	 * requested.
	 * <p>
	 * Note: This is package private in order to expose it to {@link Node}.
	 */
	ILeafData data;

	public AbstractFixedByteArrayBuffer data() {
		return data.data();
	}

	public boolean getDeleteMarker(int index) {
		return data.getDeleteMarker(index);
	}

	public int getKeyCount() {
		return data.getKeyCount();
	}

	public IRaba getKeys() {
		return data.getKeys();
	}

	public long getMaximumVersionTimestamp() {
		return data.getMaximumVersionTimestamp();
	}

	public long getMinimumVersionTimestamp() {
		return data.getMinimumVersionTimestamp();
	}

	public long getNextAddr() {
		return data.getNextAddr();
	}

	public long getPriorAddr() {
		return data.getPriorAddr();
	}

	public long getRawRecord(int index) {
		return data.getRawRecord(index);
	}

	// public int getSpannedTupleCount() {
	// return data.getSpannedTupleCount();
	// }

	public int getValueCount() {
		return data.getValueCount();
	}

	public IRaba getValues() {
		return data.getValues();
	}

	public long getVersionTimestamp(int index) {
		return data.getVersionTimestamp(index);
	}

	public boolean hasDeleteMarkers() {
		return data.hasDeleteMarkers();
	}

	public boolean hasRawRecords() {
		return data.hasRawRecords();
	}

	public boolean hasVersionTimestamps() {
		return data.hasVersionTimestamps();
	}

	public boolean isCoded() {
		return data.isCoded();
	}

	public boolean isDoubleLinked() {
		return data.isDoubleLinked();
	}

	public boolean isLeaf() {
		return data.isLeaf();
	}

	public boolean isReadOnly() {
		return data.isReadOnly();
	}

	/**
	 * Create a new empty bucket.
	 * 
	 * @param htree
	 *            A reference to the owning {@link HTree}.
	 * @param globalDepth
	 *            The size of the address space (in bits) for each buddy hash
	 *            table on a directory page. The global depth of a node is
	 *            defined recursively as the local depth of that node within its
	 *            parent. The global/local depth are not stored explicitly.
	 *            Instead, the local depth is computed dynamically when the
	 *            child will be materialized by counting the #of pointers to the
	 *            the child in the appropriate buddy hash table in the parent.
	 *            This local depth value is passed into the constructor when the
	 *            child is materialized and set as the global depth of the
	 *            child.
	 */
	BucketPage(final HTree htree, final int globalDepth) {

		super(htree, true/* dirty */, globalDepth);

		data = new MutableBucketData(//
				slotsOnPage(), // fan-out
				htree.versionTimestamps,//
				htree.deleteMarkers,//
				htree.rawRecords//
		);

	}

	/**
	 * Return <code>true</code> if there is at lease one tuple in the buddy hash
	 * bucket for the specified key.
	 * 
	 * @param key
	 *            The key.
	 * @param buddyOffset
	 *            The offset within the {@link BucketPage} of the buddy hash
	 *            bucket to be searched.
	 * 
	 * @return <code>true</code> if a tuple is found in the buddy hash bucket
	 *         for the specified key.
	 */
	boolean contains(final byte[] key, final int buddyOffset) {

		if (key == null)
			throw new IllegalArgumentException();

		// #of slots on the page.
		final int slotsOnPage = slotsOnPage(); // (1 << htree.addressBits);

		// #of address slots in each buddy hash table.
		// final int slotsPerBuddy = (1 << globalDepth);

		// // #of buddy tables on a page.
		// final int nbuddies = (slotsOnPage) / slotsPerBuddy;

		// final int lastSlot = buddyOffset + slotsPerBuddy;

		// range check buddyOffset.
		// if (buddyOffset < 0 || buddyOffset >= slotsOnPage)
		// throw new IndexOutOfBoundsException();

		/*
		 * Locate the first unassigned tuple in the buddy bucket.
		 * 
		 * TODO Faster comparison with a coded key in the raba by either (a)
		 * asking the raba to do the equals() test; or (b) copying the key from
		 * the raba into a buffer which we reuse for each test. This is another
		 * way in which the hash table keys raba differs from the btree keys
		 * raba.
		 */
		final IRaba keys = getKeys();
		for (int i = 0; i < slotsOnPage; i++) {
			if (!keys.isNull(i)) {
				if (BytesUtil.bytesEqual(key, keys.get(i))) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * There is no reason why the number of slots in a BucketPage should be the
	 * same as the number in a DirectoryPage.
	 * 
	 * @return number of slots available in this BucketPage
	 */
	final int slotsOnPage() {
		// return 16;
		return 1 << htree.addressBits;
	}

	/**
	 * Return the first value found in the buddy hash bucket for the specified
	 * key.
	 * 
	 * @param key
	 *            The key.
	 * @param buddyOffset
	 *            The offset within the {@link BucketPage} of the buddy hash
	 *            bucket to be searched.
	 * 
	 * @return The value associated with the first tuple found in the buddy hash
	 *         bucket for the specified key and <code>null</code> if no such
	 *         tuple was found. Note that the return value is not diagnostic if
	 *         the application allows <code>null</code> values into the index.
	 */
	final byte[] lookupFirst(final byte[] key, final int buddyOffset) {

		if (key == null)
			throw new IllegalArgumentException();

		// #of slots on the page.
		final int slotsOnPage = slotsOnPage();

		// // #of address slots in each buddy hash table.
		// final int slotsPerBuddy = (1 << globalDepth);

		// // #of buddy tables on a page.
		// final int nbuddies = (slotsOnPage) / slotsPerBuddy;

		// final int lastSlot = buddyOffset + slotsPerBuddy;

		// range check buddyOffset.
		if (buddyOffset < 0 || buddyOffset >= slotsOnPage)
			throw new IndexOutOfBoundsException();

		/*
		 * Locate the first unassigned tuple in the buddy bucket.
		 * 
		 * TODO Faster comparison with a coded key in the raba by either (a)
		 * asking the raba to do the equals() test; or (b) copying the key from
		 * the raba into a buffer which we reuse for each test. This is another
		 * way in which the hash table keys raba differs from the btree keys
		 * raba.
		 */
		final IRaba keys = getKeys();
		// for (int i = buddyOffset; i < lastSlot; i++) {
		// if (!keys.isNull(i)) {
		// if(BytesUtil.bytesEqual(key,keys.get(i))) {
		// return getValues().get(i);
		// }
		// }
		// }
		for (int i = 0; i < slotsOnPage; i++) {
			if (!keys.isNull(i)) {
				if (BytesUtil.bytesEqual(key, keys.get(i))) {
					return getValues().get(i);
				}
			}
		}
		return null;
	}

	/**
	 * Return an iterator which will visit each tuple in the buddy hash bucket
	 * for the specified key.
	 * 
	 * @param key
	 *            The key.
	 * @param buddyOffset
	 *            The offset within the {@link BucketPage} of the buddy hash
	 *            bucket to be searched.
	 * 
	 * @return An iterator which will visit each tuple in the buddy hash table
	 *         for the specified key and never <code>null</code>.
	 * 
	 *         TODO Specify the contract for concurrent modification both here
	 *         and on the {@link HTree#lookupAll(byte[])} methods.
	 */
	final ITupleIterator lookupAll(final byte[] key, final int buddyOffset) {

		return new BuddyBucketTupleIterator(key, this, buddyOffset);

	}

	/**
	 * Insert the tuple into the buddy bucket.
	 * 
	 * @param key
	 *            The key (all bits, all bytes).
	 * @param val
	 *            The value (optional).
	 * @param parent
	 *            The parent {@link DirectoryPage} and never <code>null</code>
	 *            (this is required for the copy-on-write pattern).
	 * @param buddyOffset
	 *            The offset into the child of the first slot for the buddy hash
	 *            table or buddy hash bucket.
	 * 
	 * @return <code>false</code> iff the buddy bucket must be split.
	 * 
	 * @throws IllegalArgumentException
	 *             if <i>key</i> is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             if <i>parent</i> is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             if <i>buddyOffset</i> is out of the allowed range.
	 */
	boolean insert(final byte[] key, final byte[] val,
			final DirectoryPage parent, final int buddyOffset) {

		if (key == null)
			throw new IllegalArgumentException();

		if (parent == null)
			throw new IllegalArgumentException();

		// #of slots on the page.
		final int slotsOnPage = slotsOnPage();

		// #of address slots in each buddy hash table.
		// final int slotsPerBuddy = (1 << globalDepth);

		// #of buddy tables on a page.
		// final int nbuddies = (slotsOnPage) / slotsPerBuddy;

		// final int lastSlot = buddyOffset + slotsPerBuddy;

		// range check buddyOffset.
		// if (buddyOffset < 0 || buddyOffset >= slotsOnPage)
		// throw new IndexOutOfBoundsException();

		// TODO if(!mutable) copyOnWrite().insert(key,val,parent,buddyOffset);

		/*
		 * Locate the first unassigned tuple in the buddy bucket.
		 * 
		 * Note: Given the IRaba data structure, this will require us to examine
		 * the keys for a null. The "keys" rabas do not allow nulls, so we will
		 * need to use a "values" raba (nulls allowed) for the bucket keys.
		 * Unless we keep the entries in a buddy bucket dense (maybe making them
		 * dense when they are persisted for faster scans, but why bother for
		 * mutable buckets?) we will have to scan the entire buddy bucket to
		 * find an open slot (or just to count the #of slots which are currently
		 * in use).
		 * 
		 * TODO Cache the location of the last known empty slot. If it is in the
		 * same buddy bucket then we can use it immediately. Otherwise we can
		 * scan for the first empty slot in the given buddy bucket.
		 */
		final MutableKeyBuffer keys = (MutableKeyBuffer) getKeys();
		final MutableValueBuffer vals = (MutableValueBuffer) getValues();

		for (int i = 0; i < slotsOnPage; i++) {
			if (keys.isNull(i)) {
				keys.nkeys++;
				keys.keys[i] = key;
				vals.nvalues++;
				vals.values[i] = val;
				// TODO deleteMarker:=false
				// TODO versionTimestamp:=...
				((HTree) htree).nentries++;
				// insert Ok.
				return true;
			}
		}

		/*
		 * Any buddy bucket which is full is split unless it is the sole buddy
		 * in the page since a split doubles the size of the buddy bucket
		 * (unless it is the only buddy on the page) and the tuple can therefore
		 * be inserted after a split. [This rule is not perfect if we allow
		 * splits to be driven by the bytes on a page, but it should still be
		 * Ok.]
		 * 
		 * Before we can split the sole buddy bucket in a page, we need to know
		 * whether or not the keys are identical. If they are then we let the
		 * page grow rather than splitting it. This can be handled insert of
		 * bucketPage.insert(). It can have a boolean which is set false as soon
		 * as it sees a key which is not the equals() to the probe key (in all
		 * bits).
		 * 
		 * Note that an allowed split always leaves enough room for another
		 * tuple (when considering only the #of tuples and not their bytes on
		 * the page). We can still be "out of space" in terms of bytes on the
		 * page, even for a single tuple. In this edge case, the tuple should
		 * really be a raw record. That is easily controlled by having a maximum
		 * inline value byte[] length for a page - probably on the order of
		 * pageSize/16 which works out to 256 bytes for a 4k page.
		 */
		// if (nbuddies != 1) {
		/*
		 * Force a split since there is more than one buddy on the page.
		 */
		// return false;
		// }

		/*
		 * There is only one buddy on the page. Now we have to figure out
		 * whether or not all keys are duplicates.
		 */
		boolean identicalKeys = true;
		for (int i = 0; i < slotsOnPage; i++) {
			if (!BytesUtil.bytesEqual(key, keys.get(i))) {
				identicalKeys = false;
				break;
			}
		}
		if (!identicalKeys) {
			/*
			 * Force a split since it is possible to redistribute some tuples.
			 */
			return false;
		}

		/*
		 * Since the page is full, we need to grow the page (or chain an
		 * overflow page) rather than splitting the page.
		 * 
		 * TODO Maybe the easiest thing to do is just double the target #of
		 * slots on the page. We would rely on keys.capacity() in this case
		 * rather than #slots. In fact, we could just reenter the method above
		 * after doubling as long as we rely on keys.capacity() in the case
		 * where nbuddies==1. [Unit test for this case.]
		 */

		throw new UnsupportedOperationException(
				"Must overflow since all keys on full buddy bucket are duplicates.");

	}

	/**
	 * Insert used when addLevel() is invoked to copy a tuple from an existing
	 * bucket page into another bucket page. This method is very similar to
	 * {@link #insert(byte[], byte[], DirectoryPage, int)}. The critical
	 * differences are: (a) it correctly handles raw records (they are not
	 * materialized during the copy); (b) it correctly handles version counters
	 * and delete markers; and (c) the #of tuples in the index is unchanged.
	 * 
	 * @param srcPage
	 *            The source {@link BucketPage}.
	 * @param srcSlot
	 *            The slot in that {@link BucketPage} having the tuple to be
	 *            copied.
	 * @param key
	 *            The key (already materialized).
	 * @param parent
	 *            The parent {@link DirectoryPage} and never <code>null</code>
	 *            (this is required for the copy-on-write pattern).
	 * @param buddyOffset
	 *            The offset into the child of the first slot for the buddy hash
	 *            table or buddy hash bucket.
	 * 
	 * @return <code>false</code> iff the buddy bucket must be split.
	 * 
	 * @throws IllegalArgumentException
	 *             if <i>key</i> is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             if <i>parent</i> is <code>null</code>.
	 * @throws IndexOutOfBoundsException
	 *             if <i>buddyOffset</i> is out of the allowed range.
	 */
	boolean insertRawTuple(final BucketPage srcPage, final int srcSlot,
			final byte[] key, final DirectoryPage parent, final int buddyOffset) {

		if (key == null)
			throw new IllegalArgumentException();

		if (parent == null)
			throw new IllegalArgumentException();

		// #of slots on the page.
		final int slotsOnPage = slotsOnPage();

		// #of address slots in each buddy hash table.
		final int slotsPerBuddy = (1 << globalDepth);

		// #of buddy tables on a page.
		final int nbuddies = slotsOnPage / slotsPerBuddy;

		final int lastSlot = buddyOffset + slotsPerBuddy;

		// range check buddyOffset.
		if (buddyOffset < 0 || buddyOffset >= slotsOnPage)
			throw new IndexOutOfBoundsException();

		// TODO if(!mutable) copyOnWrite().insert(key,val,parent,buddyOffset);

		/*
		 * Locate the first unassigned tuple in the buddy bucket.
		 * 
		 * Note: Given the IRaba data structure, this will require us to examine
		 * the keys for a null. The "keys" rabas do not allow nulls, so we will
		 * need to use a "values" raba (nulls allowed) for the bucket keys.
		 * Unless we keep the entries in a buddy bucket dense (maybe making them
		 * dense when they are persisted for faster scans, but why bother for
		 * mutable buckets?) we will have to scan the entire buddy bucket to
		 * find an open slot (or just to count the #of slots which are currently
		 * in use).
		 * 
		 * TODO Cache the location of the last known empty slot. If it is in the
		 * same buddy bucket then we can use it immediately. Otherwise we can
		 * scan for the first empty slot in the given buddy bucket.
		 */
		final MutableKeyBuffer keys = (MutableKeyBuffer) getKeys();
		final MutableValueBuffer vals = (MutableValueBuffer) getValues();

		for (int i = buddyOffset; i < lastSlot; i++) {
			if (keys.isNull(i)) {
				keys.nkeys++;
				keys.keys[i] = key;
				vals.nvalues++;
				vals.values[i] = srcPage.getKeys().get(srcSlot); // Note: DOES
																	// NOT
																	// Materialize
																	// a raw
																	// record!!!!
				// TODO deleteMarker:=false
				// TODO versionTimestamp:=...
				// ((HTree)htree).nentries++; // DO NOT increment nentries!!!!
				// insert Ok.
				return true;
			}
		}

		/*
		 * Any buddy bucket which is full is split unless it is the sole buddy
		 * in the page since a split doubles the size of the buddy bucket
		 * (unless it is the only buddy on the page) and the tuple can therefore
		 * be inserted after a split. [This rule is not perfect if we allow
		 * splits to be driven by the bytes on a page, but it should still be
		 * Ok.]
		 * 
		 * Before we can split the sole buddy bucket in a page, we need to know
		 * whether or not the keys are identical. If they are then we let the
		 * page grow rather than splitting it. This can be handled insert of
		 * bucketPage.insert(). It can have a boolean which is set false as soon
		 * as it sees a key which is not the equals() to the probe key (in all
		 * bits).
		 * 
		 * Note that an allowed split always leaves enough room for another
		 * tuple (when considering only the #of tuples and not their bytes on
		 * the page). We can still be "out of space" in terms of bytes on the
		 * page, even for a single tuple. In this edge case, the tuple should
		 * really be a raw record. That is easily controlled by having a maximum
		 * inline value byte[] length for a page - probably on the order of
		 * pageSize/16 which works out to 256 bytes for a 4k page.
		 */
		if (nbuddies != 1) {
			/*
			 * Force a split since there is more than one buddy on the page.
			 */
			return false;
		}

		/*
		 * There is only one buddy on the page. Now we have to figure out
		 * whether or not all keys are duplicates.
		 */
		boolean identicalKeys = true;
		for (int i = buddyOffset; i < buddyOffset + slotsPerBuddy; i++) {
			if (!BytesUtil.bytesEqual(key, keys.get(i))) {
				identicalKeys = false;
				break;
			}
		}
		if (!identicalKeys) {
			/*
			 * Force a split since it is possible to redistribute some tuples.
			 */
			return false;
		}

		/*
		 * Since the page is full, we need to grow the page (or chain an
		 * overflow page) rather than splitting the page.
		 * 
		 * TODO Maybe the easiest thing to do is just double the target #of
		 * slots on the page. We would rely on keys.capacity() in this case
		 * rather than #slots. In fact, we could just reenter the method above
		 * after doubling as long as we rely on keys.capacity() in the case
		 * where nbuddies==1. [Unit test for this case.]
		 */

		throw new UnsupportedOperationException(
				"Must overflow since all keys on full buddy bucket are duplicates.");

	}

	/**
	 * Return an iterator visiting all the non-deleted, non-empty tuples on this
	 * {@link BucketPage}.
	 */
	ITupleIterator tuples() {

		return new InnerBucketPageTupleIterator(IRangeQuery.DEFAULT);

	}

	/**
	 * Visits the non-empty tuples in each {@link BucketPage} visited by the
	 * source iterator.
	 */
	private class InnerBucketPageTupleIterator<E> implements ITupleIterator<E> {

		private final int slotsPerPage = slotsOnPage();

		private int nextNonEmptySlot = 0;

		private final Tuple<E> tuple;

		InnerBucketPageTupleIterator(final int flags) {

			// look for the first slot.
			if (findNextSlot()) {

				this.tuple = new Tuple<E>(htree, flags);

			} else {

				// Nothing will be visited.
				this.tuple = null;

			}

		}

		/**
		 * Scan to the next non-empty slot in the current {@link BucketPage}.
		 * 
		 * @return <code>true</code> iff there is a non-empty slot on the
		 *         current {@link BucketPage}.
		 */
		private boolean findNextSlot() {
			final IRaba keys = getKeys();
			for (; nextNonEmptySlot < slotsPerPage; nextNonEmptySlot++) {
				if (keys.isNull(nextNonEmptySlot))
					continue;
				return true;
			}
			// The current page is exhausted.
			return false;
		}

		public boolean hasNext() {

			return nextNonEmptySlot < slotsPerPage;

		}

		public ITuple<E> next() {
			if (!hasNext())
				throw new NoSuchElementException();
			// Copy the data for the current tuple into the Tuple buffer.
			tuple.copy(nextNonEmptySlot, data);
			/*
			 * Advance to the next slot on the current page. if there is non,
			 * then the current page reference will be cleared and we will need
			 * to fetch a new page in hasNext() on the next invocation.
			 */
			nextNonEmptySlot++; // skip past the current tuple.
			findNextSlot(); // find the next non-null slot (next tuple).
			// Return the Tuple buffer.
			return tuple;
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * Visits this leaf if unless it is not dirty and the flag is true, in which
	 * case the returned iterator will not visit anything.
	 * 
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Iterator<AbstractPage> postOrderNodeIterator(
			final boolean dirtyNodesOnly, final boolean nodesOnly) {

		if (dirtyNodesOnly && !isDirty()) {

			return EmptyIterator.DEFAULT;

		} else if (nodesOnly) {

			return EmptyIterator.DEFAULT;

		} else {

			return new SingleValueIterator(this);

		}

	}

	@Override
	public void PP(final StringBuilder sb) {

		sb.append(PPID() + " [" + globalDepth + "] " + indent(getLevel()));

		sb.append("("); // start of address map.

		// #of buddy tables on a page.
		// final int nbuddies = (1 << htree.addressBits) / (1 << globalDepth);
		final int nbuddies = 1;

		// #of address slots in each buddy hash table.
		// final int slotsPerBuddy = (1 << globalDepth);
		final int slotsPerBuddy = slotsOnPage();

		for (int i = 0; i < nbuddies; i++) {

			if (i > 0) // buddy boundary marker
				sb.append(";");

			for (int j = 0; j < slotsPerBuddy; j++) {

				if (j > 0) // slot boundary marker.
					sb.append(",");

				final int slot = i * slotsPerBuddy + j;

				sb.append(PPVAL(slot));

			}

		}

		sb.append(")"); // end of tuples

		sb.append("\n");

	}

	/**
	 * Pretty print a value from the tuple at the specified slot on the page.
	 * 
	 * @param index
	 *            The slot on the page.
	 * 
	 * @return The pretty print representation of the value associated with the
	 *         tuple at that slot.
	 * 
	 *         TODO Either indirect for raw records or write out the addr of the
	 *         raw record.
	 */
	private String PPVAL(final int index) {

		if (getKeys().isNull(index))
			return "-";

		final byte[] key = getKeys().get(index);

		final String keyStr = BytesUtil.toString(key) + "("
				+ BytesUtil.toBitString(key) + ")";

		final String valStr;

		if (false/* showValues */) {

			final byte[] value = getValues().get(index);

			valStr = BytesUtil.toString(value);

		} else {

			valStr = null;

		}

		if (valStr == null) {

			return keyStr;

		}

		return keyStr + "=>" + valStr;

	}

	/**
	 * Human readable representation of the {@link ILeafData} plus transient
	 * information associated with the {@link BucketPage}.
	 */
	@Override
	public String toString() {

		final StringBuilder sb = new StringBuilder();

		sb.append(super.toString());

		sb.append("{ isDirty=" + isDirty());

		sb.append(", isDeleted=" + isDeleted());

		sb.append(", addr=" + identity);

		final DirectoryPage p = (parent == null ? null : parent.get());

		sb.append(", parent=" + (p == null ? "N/A" : p.toShortString()));
		sb.append(", globalDepth=" + getGlobalDepth());
		sb.append(", nbuddies=" + (1 << htree.addressBits) / (1 << globalDepth));
		sb.append(", slotsPerBuddy=" + (1 << globalDepth));
		if (data == null) {

			// No data record? (Generally, this means it was stolen by copy on
			// write).
			sb.append(", data=NA}");

			return sb.toString();

		}

		sb.append(", nkeys=" + getKeyCount());

		// sb.append(", minKeys=" + minKeys());
		//
		// sb.append(", maxKeys=" + maxKeys());

		DefaultLeafCoder.toString(this, sb);

		sb.append("}");

		return sb.toString();

	}

	protected boolean dump(final Level level, final PrintStream out,
			final int height, final boolean recursive, final boolean materialize) {

		final boolean debug = level.toInt() <= Level.DEBUG.toInt();

		// Set to false iff an inconsistency is detected.
		boolean ok = true;

		if (parent == null || parent.get() == null) {
			out.println(indent(height) + "ERROR: parent not set");
			ok = false;
		}

		if (globalDepth > parent.get().globalDepth) {
			out.println(indent(height)
					+ "ERROR: localDepth exceeds globalDepth of parent");
			ok = false;
		}

		/*
		 * FIXME Count the #of pointers in each buddy hash table of the parent
		 * to each buddy bucket in this bucket page and verify that the
		 * globalDepth on the child is consistent with the pointers in the
		 * parent.
		 * 
		 * FIXME The same check must be performed for the directory page to
		 * cross validate the parent child linking pattern with the transient
		 * cached globalDepth fields.
		 */

		if (debug || !ok) {

			out.println(indent(height) + toString());

		}

		return ok;

	}

	/**
	 * From the current bit resolution, determines how many extra bits are
	 * required to ensure the current set of bucket values can be split.
	 * <p>
	 * The additional complexity of determining whether the page can really be
	 * split is left to the parent. A new directory, covering the required
	 * prefixBits would inititally be created with depth 1. But if the specified
	 * bit is discriminated within buddy buckets AND other bits do not further
	 * separate the buckets then the depth of the directory will need to be
	 * increased before the bucket page can be split.
	 * 
	 * @return bit depth increase from current offset required -or-
	 *         <code>-1</code> if it is not possible to split the page no matter
	 *         how many bits we have.
	 */
	int distinctBitsRequired() {
		final int currentResolution = getPrefixLength(); // start offset of this
															// page
		int testPrefix = currentResolution + 1;

		final IRaba keys = data.getKeys();
		final int nkeys = keys.size();

		int maxPrefix = 0;
		for (int t = 1; t < nkeys; t++) {
			final byte[] k = keys.get(t);
			final int klen = k == null ? 0 : k.length;
			maxPrefix = maxPrefix > klen ? maxPrefix : klen;
		}
		maxPrefix *= 8; // convert max bytes to max bits

		assert nkeys > 1;

		while (testPrefix < maxPrefix) {
			final boolean bitset = BytesUtil.getBit(keys.get(0), testPrefix);
			for (int t = 1; t < nkeys; t++) {
				final byte[] k = keys.get(t);
				if (bitset != (k == null ? false : BytesUtil.getBit(
						keys.get(t), testPrefix))) {
					return testPrefix - currentResolution;
				}
			}
			testPrefix++;
		}

		return -1;
	}

	/**
	 * To insert in a BucketPage must handle split
	 * 
	 * @see com.bigdata.htree.AbstractPage#insertRawTuple(byte[], byte[], int)
	 */
	void insertRawTuple(final byte[] key, final byte[] val, final int buddy) {
		final int slotsPerBuddy = (1 << htree.addressBits);
		final MutableKeyBuffer keys = (MutableKeyBuffer) getKeys();
		final MutableValueBuffer vals = (MutableValueBuffer) getValues();

		if (true) {
			// just fit somewhere in page
			for (int i = 0; i < slotsPerBuddy; i++) {
				if (keys.isNull(i)) {
					keys.nkeys++;
					keys.keys[i] = key;
					vals.nvalues++;
					vals.values[i] = val;
					// TODO deleteMarker:=false
					// TODO versionTimestamp:=...
					// do not increment on raw insert, since this is only ever
					// (for now) a re-organisation
					// ((HTree)htree).nentries++;
					// insert Ok.
					return;
				}
			}
		} else { // if mapping buddy explicitly
			final int buddyStart = buddy * slotsPerBuddy;
			final int lastSlot = buddyStart + slotsPerBuddy;

			for (int i = buddyStart; i < lastSlot; i++) {
				if (keys.isNull(i)) {
					keys.nkeys++;
					keys.keys[i] = key;
					vals.nvalues++;
					vals.values[i] = val;
					// TODO deleteMarker:=false
					// TODO versionTimestamp:=...
					((HTree) htree).nentries++;
					// insert Ok.
					return;
				}
			}
		}

		// unable to insert
		if (globalDepth == htree.addressBits) {
			// max depth so add level
			DirectoryPage np = ((HTree) htree).addLevel2(this);

			np.insertRawTuple(key, val, 0);
		} else {
			// otherwise split page by asking parent to split and re-inserting
			// values

			final DirectoryPage parent = getParentDirectory();
			parent.split(this);
		}
	}

}