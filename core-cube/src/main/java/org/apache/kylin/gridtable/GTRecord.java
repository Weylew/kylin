package org.apache.kylin.gridtable;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;

import org.apache.kylin.common.util.ByteArray;
import org.apache.kylin.common.util.ImmutableBitSet;

public class GTRecord implements Comparable<GTRecord> {

    final GTInfo info;
    final ByteArray[] cols;

    private ImmutableBitSet maskForEqualHashComp;

    public GTRecord(GTInfo info, ImmutableBitSet maskForEqualHashComp, ByteArray[] cols) {
        this.info = info;
        this.cols = cols;
        this.maskForEqualHashComp = maskForEqualHashComp;
    }

    public GTRecord(GTInfo info, ImmutableBitSet maskForEqualHashComp) {
        this.cols = new ByteArray[info.getColumnCount()];
        for (int i = 0; i < this.cols.length; i++) {
            // consider column projection by pass in another bit set
            this.cols[i] = new ByteArray();
        }
        this.info = info;
        this.maskForEqualHashComp = maskForEqualHashComp;

    }

    public GTRecord(GTInfo info) {
        this(info, info.colAll);
    }

    public GTRecord(GTInfo info, ByteArray[] cols) {
        this(info, info.colAll, cols);
    }

    public GTInfo getInfo() {
        return info;
    }

    public ByteArray get(int i) {
        return cols[i];
    }

    public ByteArray[] getInternal() {
        return cols;
    }

    public void set(int i, ByteArray data) {
        cols[i].set(data.array(), data.offset(), data.length());
    }

    /** set record to the codes of specified values, new space allocated to hold the codes */
    public GTRecord setValues(Object... values) {
        setValues(info.colAll, new ByteArray(info.getMaxRecordLength()), values);
        return this;
    }

    /** set record to the codes of specified values, reuse given space to hold the codes */
    public GTRecord setValues(ImmutableBitSet selectedCols, ByteArray space, Object... values) {
        assert selectedCols.cardinality() == values.length;

        ByteBuffer buf = space.asBuffer();
        int pos = buf.position();
        for (int i = 0; i < selectedCols.trueBitCount(); i++) {
            int c = selectedCols.trueBitAt(i);
            info.codeSystem.encodeColumnValue(c, values[i], buf);
            int newPos = buf.position();
            cols[c].set(buf.array(), buf.arrayOffset() + pos, newPos - pos);
            pos = newPos;
        }
        return this;
    }

    /** decode and return the values of this record */
    public Object[] getValues() {
        return getValues(info.colAll, new Object[info.getColumnCount()]);
    }

    /** decode and return the values of this record */
    public Object[] getValues(ImmutableBitSet selectedCols, Object[] result) {
        assert selectedCols.cardinality() == result.length;

        for (int i = 0; i < selectedCols.trueBitCount(); i++) {
            int c = selectedCols.trueBitAt(i);
            if (cols[c] == null || cols[c].array() == null) {
                result[i] = null;
            } else {
                result[i] = info.codeSystem.decodeColumnValue(c, cols[c].asBuffer());
            }
        }
        return result;
    }

    public Object[] getValues(int[] selectedColumns, Object[] result) {
        assert selectedColumns.length <= result.length;
        for (int i = 0; i < selectedColumns.length; i++) {
            int c = selectedColumns[i];
            if (cols[c].array() == null) {
                result[i] = null;
            } else {
                result[i] = info.codeSystem.decodeColumnValue(c, cols[c].asBuffer());
            }
        }
        return result;
    }

    public GTRecord copy() {
        return copy(info.colAll);
    }

    public GTRecord copy(ImmutableBitSet selectedCols) {
        int len = 0;
        for (int i = 0; i < selectedCols.trueBitCount(); i++) {
            int c = selectedCols.trueBitAt(i);
            len += cols[c].length();
        }

        byte[] space = new byte[len];

        GTRecord copy = new GTRecord(info, this.maskForEqualHashComp);
        int pos = 0;
        for (int i = 0; i < selectedCols.trueBitCount(); i++) {
            int c = selectedCols.trueBitAt(i);
            System.arraycopy(cols[c].array(), cols[c].offset(), space, pos, cols[c].length());
            copy.cols[c].set(space, pos, cols[c].length());
            pos += cols[c].length();
        }

        return copy;
    }

    public ImmutableBitSet maskForEqualHashComp() {
        return maskForEqualHashComp;
    }

    public void maskForEqualHashComp(ImmutableBitSet set) {
        this.maskForEqualHashComp = set;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        GTRecord o = (GTRecord) obj;
        if (this.info != o.info)
            return false;
        if (this.maskForEqualHashComp != o.maskForEqualHashComp)
            return false;
        for (int i = 0; i < maskForEqualHashComp.trueBitCount(); i++) {
            int c = maskForEqualHashComp.trueBitAt(i);
            if (this.cols[c].equals(o.cols[c]) == false) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (int i = 0; i < maskForEqualHashComp.trueBitCount(); i++) {
            int c = maskForEqualHashComp.trueBitAt(i);
            hash = (31 * hash) + cols[c].hashCode();
        }
        return hash;
    }

    @Override
    public int compareTo(GTRecord o) {
        assert this.info == o.info;
        assert this.maskForEqualHashComp == o.maskForEqualHashComp; // reference equal for performance
        IGTComparator comparator = info.codeSystem.getComparator();

        int comp = 0;
        for (int i = 0; i < maskForEqualHashComp.trueBitCount(); i++) {
            int c = maskForEqualHashComp.trueBitAt(i);
            comp = comparator.compare(cols[c], o.cols[c]);
            if (comp != 0)
                return comp;
        }
        return comp;
    }

    @Override
    public String toString() {
        return toString(maskForEqualHashComp);
    }

    public String toString(ImmutableBitSet selectedColumns) {
        Object[] values = new Object[selectedColumns.cardinality()];
        getValues(selectedColumns, values);
        return Arrays.toString(values);
    }

    // ============================================================================

    public ByteArray exportColumns(ImmutableBitSet selectedCols) {
        int len = 0;
        for (int i = 0; i < selectedCols.trueBitCount(); i++) {
            int c = selectedCols.trueBitAt(i);
            len += cols[c].length();
        }

        ByteArray buf = ByteArray.allocate(len);
        exportColumns(selectedCols, buf);
        return buf;
    }

    /** write data to given buffer, like serialize */
    public void exportColumns(ImmutableBitSet selectedCols, ByteArray buf) {
        int pos = 0;
        for (int i = 0; i < selectedCols.trueBitCount(); i++) {
            int c = selectedCols.trueBitAt(i);
            System.arraycopy(cols[c].array(), cols[c].offset(), buf.array(), buf.offset() + pos, cols[c].length());
            pos += cols[c].length();
        }
        buf.setLength(pos);
    }

    /** write data to given buffer, like serialize, UNLIKE other export this will put a prefix indicating null or not*/
    public void exportAllColumns(ByteBuffer buf) {
        for (int i = 0; i < info.colAll.trueBitCount(); i++) {
            int c = info.colAll.trueBitAt(i);
            if (cols[c] == null || cols[c].array() == null) {
                buf.put((byte) 0);
            } else {
                buf.put((byte) 1);
                buf.put(cols[c].array(), cols[c].offset(), cols[c].length());
            }
        }
    }

    /** write data to given buffer, like serialize */
    public void exportColumns(ImmutableBitSet selectedCols, ByteBuffer buf) {
        for (int i = 0; i < selectedCols.trueBitCount(); i++) {
            int c = selectedCols.trueBitAt(i);
            buf.put(cols[c].array(), cols[c].offset(), cols[c].length());
        }
    }

    public void exportColumns(int[] fieldIndex, ByteBuffer buf) {
        for (int i : fieldIndex) {
            buf.put(cols[i].array(), cols[i].offset(), cols[i].length());
        }
    }

    /** write data to given buffer, like serialize */
    public void exportColumnBlock(int c, ByteBuffer buf) {
        exportColumns(info.colBlocks[c], buf);
    }

    /** change pointers to point to data in given buffer, UNLIKE deserialize */
    public void loadPrimaryKey(ByteBuffer buf) {
        loadColumns(info.primaryKey, buf);
    }

    /** change pointers to point to data in given buffer, UNLIKE deserialize */
    public void loadCellBlock(int c, ByteBuffer buf) {
        loadColumns(info.colBlocks[c], buf);
    }

    /** change pointers to point to data in given buffer, UNLIKE deserialize */
    public void loadAllColumns(ByteBuffer buf) {
        int pos = buf.position();
        for (int i = 0; i < info.colAll.trueBitCount(); i++) {
            int c = info.colAll.trueBitAt(i);

            byte exist = buf.get();
            pos++;

            if (exist == 1) {
                int len = info.codeSystem.codeLength(c, buf);
                cols[c].set(buf.array(), buf.arrayOffset() + pos, len);
                pos += len;
                buf.position(pos);
            }
        }
    }

    /** change pointers to point to data in given buffer, UNLIKE deserialize */
    public void loadColumns(ImmutableBitSet selectedCols, ByteBuffer buf) {
        int pos = buf.position();
        for (int i = 0; i < selectedCols.trueBitCount(); i++) {
            int c = selectedCols.trueBitAt(i);
            int len = info.codeSystem.codeLength(c, buf);
            cols[c].set(buf.array(), buf.arrayOffset() + pos, len);
            pos += len;
            buf.position(pos);
        }
    }

    /** similar to export(primaryKey) but will stop at the first null value */
    public static ByteArray exportScanKey(GTRecord rec) {
        if (rec == null)
            return null;

        GTInfo info = rec.getInfo();

        BitSet selectedColumns = new BitSet();
        int len = 0;
        for (int i = 0; i < info.primaryKey.trueBitCount(); i++) {
            int c = info.primaryKey.trueBitAt(i);
            if (rec.cols[c].array() == null) {
                break;
            }
            selectedColumns.set(c);
            len += rec.cols[c].length();
        }

        if (selectedColumns.cardinality() == 0)
            return null;

        ByteArray buf = ByteArray.allocate(len);
        rec.exportColumns(new ImmutableBitSet(selectedColumns), buf);
        return buf;
    }

}
