/*
 *     Copyright (C) 2015  higherfrequencytrading.com
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.chronicle.wire;

import net.openhft.chronicle.bytes.*;
import net.openhft.chronicle.core.Maths;
import net.openhft.chronicle.core.pool.ClassAliasPool;
import net.openhft.chronicle.core.util.BooleanConsumer;
import net.openhft.chronicle.core.util.ObjectUtils;
import net.openhft.chronicle.core.util.StringUtils;
import net.openhft.chronicle.core.values.IntValue;
import net.openhft.chronicle.core.values.LongArrayValues;
import net.openhft.chronicle.core.values.LongValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xerial.snappy.Snappy;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.openhft.chronicle.bytes.Bytes.empty;
import static net.openhft.chronicle.bytes.NativeBytes.nativeBytes;
import static net.openhft.chronicle.core.util.ReadResolvable.readResolve;

/**
 * JSON wire format
 * <p>
 * At the moment, this is a cut down version of the YAML wire format.
 */
public class JSONWire extends TextWire {
    static final BytesStore COMMA = BytesStore.from(",");

    public JSONWire(Bytes bytes, boolean use8bit) {
        super(bytes, use8bit);
    }

    public JSONWire(Bytes bytes) {
        this(bytes, false);
    }

    @NotNull
    public static JSONWire from(@NotNull String text) {
        return new JSONWire(Bytes.from(text));
    }

    public static String asText(@NotNull Wire wire) {
        long pos = wire.bytes().readPosition();
        JSONWire tw = new JSONWire(nativeBytes());
        wire.copyTo(tw);
        wire.bytes().readPosition(pos);

        return tw.toString();
    }

    public static <ACS extends Appendable & CharSequence> void unescape(@NotNull ACS sb) {
        int end = 0;
        int length = sb.length();
        for (int i = 0; i < length; i++) {
            char ch = sb.charAt(i);
            if (ch == '\\' && i < length - 1) {
                char ch3 = sb.charAt(++i);
                switch (ch3) {
                    case 'n':
                        ch = '\n';
                        break;
                    case 't':
                        ch = '\t';
                        break;
                    default:
                        ch = ch3;
                }
            }
            AppendableUtil.setCharAt(sb, end++, ch);
        }
        if (length != sb.length())
            throw new IllegalStateException("Length changed from " + length + " to " + sb.length() + " for " + sb);
        AppendableUtil.setLength(sb, end);
    }

    @Override
    public void copyTo(@NotNull WireOut wire) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public ValueIn read() {
        readField(Wires.acquireStringBuilder());
        return valueIn;
    }

    @NotNull
    private StopCharsTester getEscapingEndOfText() {
        StopCharsTester escaping = ESCAPED_END_OF_TEXT.get();
        // reset it.
        escaping.isStopChar(' ', ' ');
        return escaping;
    }

    private StopCharTester getEscapingQuotes() {
        StopCharTester sct = ESCAPED_QUOTES.get();
        // reset it.
        sct.isStopChar(' ');
        return sct;
    }

    void consumeWhiteSpace() {
        for (; ; ) {
            int codePoint = peekCode();
            if (codePoint == '#') {
                //noinspection StatementWithEmptyBody
                while (readCode() >= ' ') ;
            } else if (Character.isWhitespace(codePoint) || codePoint == ',') {
                bytes.readSkip(1);
            } else {
                break;
            }
        }
    }

    int peekCode() {
        return bytes.peekUnsignedByte();
    }

    /**
     * returns true if the next string is {@code str}
     *
     * @param source string
     * @return true if the strings are the same
     */
    private boolean peekStringIgnoreCase(@NotNull final String source) {
        if (source.isEmpty())
            return true;

        if (bytes.readRemaining() < 1)
            return false;

        long pos = bytes.readPosition();

        try {
            for (int i = 0; i < source.length(); i++) {
                if (Character.toLowerCase(source.charAt(i)) != Character.toLowerCase(bytes.readByte()))
                    return false;
            }
        } finally {
            bytes.readPosition(pos);
        }

        return true;
    }

    private int readCode() {
        if (bytes.readRemaining() < 1)
            return -1;
        return bytes.readUnsignedByte();
    }

    @NotNull
    @Override
    public ValueIn read(@NotNull WireKey key) {
        long position = bytes.readPosition();
        StringBuilder sb = readField(Wires.acquireStringBuilder());
        if (sb.length() == 0 || StringUtils.isEqual(sb, key.name()))
            return valueIn;
        bytes.readPosition(position);
        throw new UnsupportedOperationException("Unordered fields not supported yet. key=" + key
                .name() + ", was=" + sb + ", data='" + sb + "'");
    }

    @NotNull
    @Override
    public ValueIn read(@NotNull StringBuilder name) {
        consumeWhiteSpace();
        readField(name);
        return valueIn;
    }

    @NotNull
    @Override
    public ValueIn getValueIn() {
        return valueIn;
    }

    @NotNull
    @Override
    public Wire readComment(@NotNull StringBuilder s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        bytes.clear();
    }

    @NotNull
    @Override
    public Bytes<?> bytes() {
        return bytes;
    }

    @Override
    public boolean hasMore() {
        consumeWhiteSpace();

        return bytes.readRemaining() > 0;
    }

    @NotNull
    @Override
    public ValueOut write() {
        return valueOut.write();
    }

    @NotNull
    @Override
    public ValueOut write(@NotNull WireKey key) {
        return valueOut.write(key);
    }

    @NotNull
    @Override
    public ValueOut writeValue() {
        return valueOut;
    }

    @NotNull
    @Override
    public ValueOut getValueOut() {
        return valueOut;
    }

    @NotNull
    @Override
    public Wire writeComment(@NotNull CharSequence s) {
        valueOut.writeComment(s);
        return this;
    }

    @NotNull
    @Override
    public WireOut addPadding(int paddingToAdd) {
        for (int i = 0; i < paddingToAdd; i++)
            bytes.append((bytes.writePosition() & 63) == 0 ? '\n' : ' ');
        return this;
    }

    void escaped(@NotNull CharSequence s) {
        if (needsQuotes(s) == Quotes.NONE) {
            bytes.append(s);
            return;
        }
        doEscape(s);
    }

    private void doEscape(@NotNull CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':
                case '\\':
                    bytes.append('\\').append(ch);
                    break;

                case '\n':
                    bytes.append("\\n");
                    break;
                default:
                    bytes.append(ch);
                    break;
            }
        }
    }


    @NotNull
    @Override
    public LongValue newLongReference() {
        return new TextLongReference();
    }


    @NotNull
    @Override
    public IntValue newIntReference() {
        return new TextIntReference();
    }

    @NotNull
    @Override
    public LongArrayValues newLongArrayReference() {
        return new TextLongArrayReference();
    }

    public void parseWord(StringBuilder sb) {
        parseUntil(sb, StopCharTesters.SPACE_STOP);
    }

    public void parseUntil(StringBuilder sb, StopCharTester testers) {
        if (use8bit)
            bytes.parse8bit(sb, testers);
        else
            bytes.parseUTF(sb, testers);
    }

    public void parseUntil(StringBuilder sb, StopCharsTester testers) {
        sb.setLength(0);
        if (use8bit) {
            AppendableUtil.read8bitAndAppend(bytes, sb, testers);
        } else {
            try {
                AppendableUtil.readUTFAndAppend(bytes, sb, testers);
            } catch (IOException e) {
                throw new IORuntimeException(e);
            }
        }
    }

    public void append(CharSequence cs) {
        if (use8bit)
            bytes.append8bit(cs);
        else
            bytes.append(cs);
    }

    public void append(CharSequence cs, int offset) {
        if (use8bit)
            bytes.append8bit(cs, offset, offset + cs.length());
        else
            bytes.append(cs, offset, offset + cs.length());
    }

    class TextValueOut implements ValueOut {
        @NotNull
        BytesStore sep = Bytes.empty();
        boolean leaf = false;

        void prependSeparator() {
            append(sep);
            if (sep.endsWith('\n'))
                indent();
            sep = Bytes.empty();
        }

        @NotNull
        @Override
        public ValueOut leaf() {
            leaf = true;
            return this;
        }

        @NotNull
        @Override
        public WireOut wireOut() {
            return JSONWire.this;
        }

        private void indent() {
        }

        public void elementSeparator() {
            sep = COMMA;
        }

        @NotNull
        @Override
        public WireOut bool(@Nullable Boolean flag) {
            prependSeparator();
            append(flag == null ? "!" + NULL : flag ? "true" : "false");
            elementSeparator();
            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut text(@Nullable CharSequence s) {
            prependSeparator();
            if (s == null) {
                append("!" + NULL);
            } else {
                bytes.append('"');
                escaped(s);
                bytes.append('"');
            }
            elementSeparator();
            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut int8(byte i8) {
            prependSeparator();
            bytes.append(i8);
            elementSeparator();
            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut bytes(@Nullable BytesStore fromBytes) {

            if (isText(fromBytes))
                return text(fromBytes);

            int length = Maths.toInt32(fromBytes.readRemaining());
            byte[] byteArray = new byte[length];
            fromBytes.copyTo(byteArray);
            return bytes(byteArray);
        }

        @NotNull
        @Override
        public WireOut rawBytes(@NotNull byte[] value) {
            prependSeparator();
            bytes.write(value);
            elementSeparator();
            return JSONWire.this;
        }

        private boolean isText(@Nullable BytesStore fromBytes) {

            if (fromBytes == null)
                return true;
            for (long i = fromBytes.readPosition(); i < fromBytes.readLimit(); i++) {
                int ch = fromBytes.readUnsignedByte(i);
                if ((ch < ' ' && ch != '\t') || ch >= 127)
                    return false;
            }
            return true;
        }

        @NotNull
        @Override
        public ValueOut writeLength(long remaining) {
            throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public WireOut bytes(byte[] byteArray) {
            prependSeparator();
            append("!!binary ");
            append(Base64.getEncoder().encodeToString(byteArray));
            append(END_FIELD);
            elementSeparator();

            return JSONWire.this;
        }

        @Override
        public WireOut snappy(byte[] compressedBytes) {
            prependSeparator();
            append("!!snappy ");
            append(Base64.getEncoder().encodeToString(compressedBytes));
            append(END_FIELD);
            elementSeparator();

            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut uint8checked(int u8) {
            prependSeparator();
            bytes.append(u8);
            elementSeparator();

            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut int16(short i16) {
            prependSeparator();
            bytes.append(i16);
            elementSeparator();

            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut uint16checked(int u16) {
            prependSeparator();
            bytes.append(u16);
            elementSeparator();

            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut utf8(int codepoint) {
            prependSeparator();
            StringBuilder sb = Wires.acquireStringBuilder();
            sb.appendCodePoint(codepoint);
            text(sb);
            sep = empty();
            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut int32(int i32) {
            prependSeparator();
            bytes.append(i32);
            elementSeparator();

            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut uint32checked(long u32) {
            prependSeparator();
            bytes.append(u32);
            elementSeparator();

            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut int64(long i64) {
            prependSeparator();
            bytes.append(i64);
            elementSeparator();

            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut int64array(long capacity) {
            TextLongArrayReference.write(bytes, capacity);
            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut int64array(long capacity, @NotNull LongArrayValues values) {
            long pos = bytes.writePosition();
            TextLongArrayReference.write(bytes, capacity);
            ((Byteable) values).bytesStore(bytes, pos, bytes.writePosition() - pos);
            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut float32(float f) {
            prependSeparator();
            bytes.append(f);
            elementSeparator();

            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut float64(double d) {
            prependSeparator();
            bytes.append(d);
            elementSeparator();

            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut time(@NotNull LocalTime localTime) {
            prependSeparator();
            append(localTime.toString());
            elementSeparator();

            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut zonedDateTime(@NotNull ZonedDateTime zonedDateTime) {
            prependSeparator();
            append(zonedDateTime.toString());
            elementSeparator();

            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut date(@NotNull LocalDate localDate) {
            prependSeparator();
            append(localDate.toString());
            elementSeparator();

            return JSONWire.this;
        }

        @NotNull
        @Override
        public ValueOut type(@NotNull CharSequence typeName) {
            prependSeparator();
            bytes.append('!');
            append(typeName);
            sep = SPACE;
            return this;
        }

        @NotNull
        @Override
        public WireOut typeLiteral(@NotNull BiConsumer<Class, Bytes> typeTranslator, Class type) {
            prependSeparator();
            append(TYPE);
            typeTranslator.accept(type, bytes);
            elementSeparator();
            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut typeLiteral(@NotNull CharSequence type) {
            prependSeparator();
            append(TYPE);
            text(type);
            elementSeparator();
            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut uuid(@NotNull UUID uuid) {
            prependSeparator();
            append(sep);
            append(uuid.toString());
            elementSeparator();
            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut int32forBinding(int value) {
            prependSeparator();
            TextIntReference.write(bytes, value);
            elementSeparator();
            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut int32forBinding(int value, IntValue intValue) {
            throw new UnsupportedOperationException("todo");
        }

        @NotNull
        @Override
        public WireOut int64forBinding(long value) {
            prependSeparator();
            TextLongReference.write(bytes, value);
            elementSeparator();
            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut int64forBinding(long value, LongValue longValue) {
            throw new UnsupportedOperationException("todo");
        }

        @NotNull
        @Override
        public WireOut sequence(@NotNull Consumer<ValueOut> writer) {
            pushState();
            bytes.append('[');
            sep = NEW_LINE;
            long pos = bytes.readPosition();
            writer.accept(this);
            if (bytes.writePosition() > pos + 1)
                bytes.append('\n');

            popState();
            indent();
            bytes.append(']');
            sep = END_FIELD;
            return JSONWire.this;
        }

        @Override
        public WireOut array(@NotNull Consumer<ValueOut> writer, Class arrayType) {
            if (arrayType == String[].class) append("!String[] ");
            else {
                bytes.append('!');
                append(arrayType.getName());
                bytes.append(' ');
            }
            ;
            return sequence(writer);
        }

        private void popState() {
            leaf = false;
        }

        private void pushState() {

        }

        @NotNull
        @Override
        public WireOut marshallable(@NotNull WriteMarshallable object) {
            if (!leaf)
                pushState();

            prependSeparator();
            bytes.append('{');
            sep = leaf ? SPACE : END_FIELD;

            object.writeMarshallable(JSONWire.this);

            if (!leaf)
                popState();
            else
                leaf = false;
            if (sep.startsWith(','))
                append(sep, 1);
            else
                prependSeparator();
            bytes.append('}');

            sep = COMMA;
            return JSONWire.this;
        }

        @NotNull
        @Override
        public WireOut map(@NotNull final Map map) {
            type(SEQ_MAP);
            bytes.append(' ');
            bytes.append('[');
            pushState();
            sep = END_FIELD;
            map.forEach((k, v) -> {
                prependSeparator();
                append("{ key: ");
                leaf();
                object2(k);
                sep = COMMA;
                prependSeparator();
                append("  value: ");
                leaf();
                object2(v);
                bytes.append(' ');
                bytes.append('}');
                sep = COMMA;
            });
            popState();
            sep = END_FIELD;
            prependSeparator();
            bytes.append(']');
            sep = END_FIELD;
            return JSONWire.this;
        }

        private void object2(Object v) {
            if (v instanceof CharSequence)
                text((CharSequence) v);
            else if (v instanceof WriteMarshallable)
                typedMarshallable((WriteMarshallable) v);
            else if (v == null)
                append("!" + NULL);
            else
                text(String.valueOf(v));
        }

        @NotNull
        @Override
        public WireOut typedMap(@NotNull Map<? extends WriteMarshallable, ? extends Marshallable> map) {
            type(SEQ_MAP);
            map.forEach((k, v) -> sequence(w -> w.marshallable(m -> m
                    .write(() -> "key").typedMarshallable(k)
                    .write(() -> "value").typedMarshallable(v))));
            return JSONWire.this;
        }

        @NotNull
        public ValueOut write() {
            append(sep);
            bytes.append('"');
            bytes.append('"');
            bytes.append(':');
            sep = empty();
            return this;
        }

        @NotNull
        public ValueOut write(@NotNull WireKey key) {
            CharSequence name = key.name();
            if (name == null) name = Integer.toString(key.code());
            prependSeparator();
            bytes.append('"');
            escaped(name);
            bytes.append('"');
            bytes.append(':');
            return this;
        }

        public void writeComment(@NotNull CharSequence s) {
        }
    }

    class JSONValueIn extends TextValueIn {
        @Override
        public String text() {
            return StringUtils.toString(textTo(Wires.acquireStringBuilder()));
        }

        @Nullable
        @Override
        public StringBuilder textTo(@NotNull StringBuilder sb) {
            return textTo0(sb);
        }

        @Nullable
        @Override
        public Bytes textTo(@NotNull Bytes bytes) {
            return textTo0(bytes);
        }

        @Nullable
        <ACS extends Appendable & CharSequence> ACS textTo0(@NotNull ACS a) {
            consumeWhiteSpace();
            int ch = peekCode();

            if (ch == '{') {
                final long len = readLength();
                try {
                    a.append(Bytes.toString(bytes, bytes.readPosition(), len));
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
                bytes.readSkip(len);

                // read the next comma
                bytes.skipTo(StopCharTesters.COMMA_STOP);

                return a;

            } else if (ch == '"') {
                bytes.readSkip(1);
                if (use8bit)
                    bytes.parse8bit(a, getEscapingQuotes());
                else
                    bytes.parseUTF(a, getEscapingQuotes());
                unescape(a);
                int code = peekCode();
                if (code == '"')
                    readCode();

            } else if (ch == '!') {
                bytes.readSkip(1);
                ch = peekCode();
                if (ch == '!') {
                    bytes.readSkip(1);
                    StringBuilder sb = Wires.acquireStringBuilder();
                    parseWord(sb);
                    if (StringUtils.isEqual(sb, "null")) {
                        textTo(sb);
                        return null;
                    } else if (StringUtils.isEqual(sb, "snappy")) {
                        textTo(sb);
                        try {
                            //todo needs to be made efficient
                            byte[] decodedBytes = Base64.getDecoder().decode(sb.toString().getBytes());
                            String csq = Snappy.uncompressString(decodedBytes);
                            return (ACS) Wires.acquireStringBuilder().append(csq);
                        } catch (IOException e) {
                            throw new AssertionError(e);
                        }
                    }
                } else {
                    StringBuilder sb = Wires.acquireStringBuilder();
                    textTo(sb);
                    // ignore the type.
                }

            } else {
                if (bytes.readRemaining() > 0) {
                    if (a instanceof Bytes || use8bit)
                        bytes.parse8bit(a, getEscapingEndOfText());
                    else
                        bytes.parseUTF(a, getEscapingEndOfText());

                } else {
                    AppendableUtil.setLength(a, 0);
                }
                // trim trailing spaces.
                while (a.length() > 0)
                    if (Character.isWhitespace(a.charAt(a.length() - 1)))
                        AppendableUtil.setLength(a, a.length() - 1);
                    else
                        break;
            }

            int prev = rewindAndRead();
            if (prev == ':')
                bytes.readSkip(-1);
            return a;
        }

        private int rewindAndRead() {
            return bytes.readPosition() > 0 ? bytes.readUnsignedByte(bytes.readPosition() - 1) : -1;
        }
        @NotNull
        @Override
        public WireIn bytesMatch(@NotNull BytesStore compareBytes, BooleanConsumer consumer) {
            throw new UnsupportedOperationException("todo");
        }

        @NotNull
        @Override
        public WireIn bytes(@NotNull Bytes toBytes) {
            return bytes(wi -> toBytes.write(wi.bytes()));
        }

        @NotNull
        public WireIn bytes(@NotNull ReadMarshallable bytesConsumer) {
            consumeWhiteSpace();

            // TODO needs to be made much more efficient.
            StringBuilder sb = Wires.acquireStringBuilder();
            if (peekCode() == '!') {
                parseWord(sb);
                String str = Wires.INTERNER.intern(sb);
                if (str.equals("!!binary")) {
                    AppendableUtil.setLength(sb, 0);
                    parseWord(sb);
                    byte[] decode = Base64.getDecoder().decode(sb.toString());
                    bytesConsumer.readMarshallable(new JSONWire(Bytes.wrapForRead(decode)));

                } else if (str.equals("!!null")) {
                    bytesConsumer.readMarshallable(null);
                    parseWord(sb);
                } else {
                    throw new IORuntimeException("Unsupported type=" + str);
                }
            } else {
                textTo(sb);
                bytesConsumer.readMarshallable(new JSONWire(Bytes.wrapForRead(sb.toString().getBytes())));
            }
            return JSONWire.this;
        }

        @Nullable
        public byte[] bytes() {
            consumeWhiteSpace();
            // TODO needs to be made much more efficient.
            StringBuilder sb = Wires.acquireStringBuilder();
            if (peekCode() == '!') {
                parseWord(sb);
                String str = Wires.INTERNER.intern(sb);
                if (str.equals("!!binary")) {
                    AppendableUtil.setLength(sb, 0);
                    parseWord(sb);
                    byte[] decode = Base64.getDecoder().decode(Wires.INTERNER.intern(sb));
                    return decode;

                } else if (str.equals("!!null")) {
                    parseWord(sb);
                    return null;
                } else if (str.equals("!" + SEQ_MAP)) {
                    sb.append(bytes.toString());
                    // todo fix this.
                    return Wires.INTERNER.intern(sb).getBytes();

                } else {
                    throw new IllegalStateException("unsupported type=" + str);
                }

            } else {
                textTo(sb);
                // todo fix this.
                return sb.toString().getBytes();
            }
        }

        @Nullable
        @Override
        public byte[] snappy() {
            consumeWhiteSpace();
            // TODO needs to be made much more efficient.
            StringBuilder sb = Wires.acquireStringBuilder();
            if (peekCode() == '!') {
                parseWord(sb);
                String str = Wires.INTERNER.intern(sb);
                if (str.equals("!!snappy")) {
                    AppendableUtil.setLength(sb, 0);
                    parseWord(sb);
                    byte[] decode = Base64.getDecoder().decode(Wires.INTERNER.intern(sb));
                    return decode;
                }
                throw new AssertionError("Incorrect format for snappy");
            } else {
                textTo(sb);
                // todo fix this.
                return sb.toString().getBytes();
            }
        }


        @NotNull
        @Override
        public WireIn wireIn() {
            return JSONWire.this;
        }

        @Override
        public long readLength() {
            consumeWhiteSpace();
            long start = bytes.readPosition();
            try {
                consumeWhiteSpace();
                int code = readCode();
                switch (code) {
                    case '{': {
                        int count = 1;
                        for (; ; ) {
                            byte b = bytes.readByte();
                            if (b == '{') {
                                count += 1;
                            } else if (b == '}') {
                                count -= 1;
                                if (count == 0)
                                    return bytes.readPosition() - start;
                            } else if (b <= 0) {
                                return bytes.readPosition() - start - 1;
                            }

                            // do nothing
                        }
                    }

                    case '-': {
                        for (; ; ) {
                            byte b = bytes.readByte();
                            if (b < ' ')
                                return bytes.readLimit() - start - 1;
                            // do nothing
                        }
                    }

                    default:
                        // TODO needs to be made much more efficient.
                        bytes();
                        return bytes.readPosition() - start;
                }
            } finally {
                bytes.readPosition(start);
            }
        }

        private long readLengthMarshallable() {
            long start = bytes.readPosition();
            try {
                consumeWhiteSpace();
                int code = readCode();
                switch (code) {
                    case '{': {
                        int count = 1;
                        for (; ; ) {
                            byte b = bytes.readByte();
                            if (b == '{') {
                                count += 1;
                            } else if (b == '}') {
                                count -= 1;
                                if (count == 0)
                                    return bytes.readPosition() - start;
                            } else if (b == 0) {
                                return bytes.readPosition() - start - 1;
                            }
                            // do nothing
                        }
                    }

                    default:
                        // TODO needs to be made much more efficient.
                        bytes();
                        return bytes.readPosition() - start;
                }
            } finally {
                bytes.readPosition(start);
            }
        }

        @Override
        public boolean hasNext() {
            return bytes.readRemaining() > 0;
        }

        @Override
        public boolean hasNextSequenceItem() {
            consumeWhiteSpace();
            int ch = peekCode();
            if (ch == ',') {
                bytes.readSkip(1);
                return true;
            }
            return ch > 0 && ch != ']';
        }

        @NotNull
        @Override
        public WireIn int64(@Nullable LongValue value) {
            consumeWhiteSpace();
            Byteable b = (Byteable) value;
            long length = b.maxSize();
            b.bytesStore(bytes, bytes.readPosition(), length);
            bytes.readSkip(length);
            consumeWhiteSpace();
            if (peekCode() == ',')
                bytes.readSkip(1);
            return JSONWire.this;
        }

        @Override
        public <T> T applyToMarshallable(@NotNull Function<WireIn, T> marshallableReader) {
            consumeWhiteSpace();
            int code = peekCode();
            if (code != '{')
                throw new IORuntimeException("Unsupported type " + (char) code);

            final long len = readLengthMarshallable();

            final long limit = bytes.readLimit();
            final long position = bytes.readPosition();

            try {
                // ensure that you can read past the end of this marshable object
                final long newLimit = position - 1 + len;
                bytes.readLimit(newLimit);
                bytes.readSkip(1); // skip the {
                consumeWhiteSpace();
                return marshallableReader.apply(JSONWire.this);
            } finally {
                bytes.readLimit(limit);

                consumeWhiteSpace();
                code = readCode();
                if (code != '}')
                    throw new IORuntimeException("Unterminated { while reading marshallable "
                            + "bytes=" + Bytes.toString(bytes)
                    );
            }
        }

        @NotNull
        String stringForCode(int code) {
            return code < 0 ? "Unexpected end of input" : "'" + (char) code + "'";
        }

        @NotNull
        @Override
        public WireIn marshallable(@NotNull ReadMarshallable object) {
            consumeWhiteSpace();
            int code = peekCode();
            if (code == '!')
                typePrefix();
            else if (code != '{')
                throw new IORuntimeException("Unsupported type " + stringForCode(code));

            final long len = readLengthMarshallable();

            final long limit = bytes.readLimit();
            final long position = bytes.readPosition();

            final long newLimit = position - 1 + len;
            try {
                // ensure that you can read past the end of this marshable object

                bytes.readLimit(newLimit);
                bytes.readSkip(1); // skip the {
                consumeWhiteSpace();
                object.readMarshallable(JSONWire.this);
            } finally {
                bytes.readLimit(limit);
                bytes.readPosition(newLimit);
            }

            consumeWhiteSpace();
            code = readCode();
            if (code != '}')
                throw new IORuntimeException("Unterminated { while reading marshallable " +
                        object + ",code='" + (char) code + "', bytes=" + Bytes.toString(bytes)
                );
            return JSONWire.this;
        }

        @Nullable
        public <T extends ReadMarshallable> T typedMarshallable() {
            try {
                consumeWhiteSpace();
                int code = peekCode();
                if (code < 0)
                    throw new IllegalStateException("Cannot read nothing as a Marshallable " + bytes.toDebugString());
                StringBuilder sb = Wires.acquireStringBuilder();
                if (code != '!')
                    throw new ClassCastException("Cannot convert to Marshallable. " + bytes.toDebugString());

                readCode();
                parseUntil(sb, TextStopCharTesters.END_OF_TYPE);

                if (StringUtils.isEqual(sb, "!null")) {
                    text();
                    return null;
                }

                if (StringUtils.isEqual(sb, "!binary")) {
                    bytesStore();
                    return null;
                }

                // its possible that the object that you are allocating may not have a
                // default constructor
                final Class clazz = ClassAliasPool.CLASS_ALIASES.forName(sb);

                if (!ReadMarshallable.class.isAssignableFrom(clazz))
                    throw new ClassCastException("Cannot convert " + sb + " to Marshallable.");

                final ReadMarshallable m = ObjectUtils.newInstance((Class<ReadMarshallable>) clazz);

                marshallable(m);
                return readResolve(m);
            } catch (Exception e) {
                throw new IORuntimeException(e);
            }
        }

        @Nullable
        @Override
        public <K, V> Map<K, V> map(@NotNull final Class<K> kClazz,
                                    @NotNull final Class<V> vClass,
                                    @NotNull final Map<K, V> usingMap) {
            consumeWhiteSpace();
            usingMap.clear();

            StringBuilder sb = Wires.acquireStringBuilder();
            if (peekCode() == '!') {
                parseUntil(sb, StopCharTesters.SPACE_STOP);
                String str = Wires.INTERNER.intern(sb);

                if (("!!null").contentEquals(sb)) {
                    text();
                    return null;

                } else if (("!" + SEQ_MAP).contentEquals(sb)) {
                    consumeWhiteSpace();
                    int start = readCode();
                    if (start != '[')
                        throw new IORuntimeException("Unsupported start of sequence : " + (char) start);
                    do {
                        marshallable(r -> {
                            final K k = r.read(() -> "key")
                                    .object(kClazz);
                            final V v = r.read(() -> "value")
                                    .object(vClass);
                            usingMap.put(k, v);
                        });
                    } while (hasNextSequenceItem());
                    return usingMap;

                } else {
                    throw new IORuntimeException("Unsupported type :" + str);
                }
            }
            return usingMap;
        }

        @Override
        public boolean bool() {
            consumeWhiteSpace();
            StringBuilder sb = Wires.acquireStringBuilder();
            if (textTo(sb) == null)
                throw new NullPointerException("value is null");

            return StringUtils.isEqual(sb, "true");
        }

        public byte int8() {
            long l = int64();
            if (l > Byte.MAX_VALUE || l < Byte.MIN_VALUE)
                throw new IllegalStateException("value=" + l + ", is greater or less than Byte.MAX_VALUE/MIN_VALUE");
            return (byte) l;
        }

        public short int16() {
            long l = int64();
            if (l > Short.MAX_VALUE || l < Short.MIN_VALUE)
                throw new IllegalStateException("value=" + l + ", is greater or less than Short.MAX_VALUE/MIN_VALUE");
            return (short) l;
        }

        public int int32() {
            long l = int64();
            if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE)
                throw new IllegalStateException("value=" + l + ", is greater or less than Integer.MAX_VALUE/MIN_VALUE");
            return (int) l;
        }

        public int uint16() {
            long l = int64();
            if (l > Integer.MAX_VALUE || l < 0)
                throw new IllegalStateException("value=" + l + ", is greater or less than Integer" +
                        ".MAX_VALUE/ZERO");
            return (int) l;
        }

        @Override
        public long int64() {
            consumeWhiteSpace();
            return bytes.parseLong();
        }

        @Override
        public double float64() {
            consumeWhiteSpace();
            return bytes.parseDouble();
        }

        @Override
        public float float32() {

            double d = float64();
            if ((double) (((float) d)) != d)
                throw new IllegalStateException("value=" + d + " can not be represented as a float");

            return (float) d;
        }

        /**
         * @return true if !!null "", if {@code true} reads the !!null "" up to the next STOP, if
         * {@code false} no  data is read  ( data is only peaked if {@code false} )
         */
        public boolean isNull() {
            consumeWhiteSpace();

            if (peekStringIgnoreCase("!!null \"\"")) {
                bytes.readSkip("!!null \"\"".length());
                // discard the text after it.
                //  text(Wires.acquireStringBuilder());
                return true;
            }

            return false;
        }

        @Override
        @Nullable
        public <E> E object(@Nullable E using,
                            @NotNull Class<E> clazz) {
            return ObjectUtils.convertTo(clazz, object0(using, clazz));
        }
    }
}
