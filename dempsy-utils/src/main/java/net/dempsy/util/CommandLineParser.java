/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dempsy.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * This is a class for constructing a hash map from parameter list. It
 * will allow the lookup of options using the name of the option.
 * For example:
 * <P>
 *
 * &nbsp;&nbsp;&nbsp;&nbsp; java myclass -option value
 * <P>
 *
 * will allow the following:
 * <P>
 *
 * &nbsp;&nbsp; static public main(String [] args) {<BR>
 * &nbsp;&nbsp;&nbsp;&nbsp; CommandLineParser clp = new CommandLineParser(args);<BR>
 * &nbsp;&nbsp;&nbsp;&nbsp; String val = clp["option"];<BR>
 * &nbsp;&nbsp;&nbsp;&nbsp; System.out.println ("option = " + val);<BR>
 * &nbsp;&nbsp; }
 * <P>
 *
 * to yield:
 * <P>
 *
 * &nbsp;&nbsp; option = value
 * <P>
 *
 * The following:
 * <P>
 *
 * &nbsp;&nbsp; java myclass unoptionedvalue -option1 value1 -option2
 * -option3 value3
 * <P>
 *
 * will result in the following map:
 * <P></P>
 * <P></P>
 *
 * <table BORDER="1">
 * <caption></caption>
 * <tr>
 * <td><B>name</B></td>
 * <td><B>value</B></td>
 * </tr>
 * <tr>
 * <td>1) unoptionedvalue</td>
 * <td>"true"</td>
 * </tr>
 * <tr>
 * <td>2) option1</td>
 * <td>value1</td>
 * </tr>
 * <tr>
 * <td>3) option2</td>
 * <td>"true"</td>
 * </tr>
 * <tr>
 * <td>4) option3</td>
 * <td>value3</td>
 * </tr>
 * </table>
 * <P>
 *
 * <B> Note: even though unoptionedvalue is a value and not a name, it
 * appears in the name field of the map entry. This may be changed in the
 * future.</B>
 */

public class CommandLineParser extends HashMap<String, String> {

    private static final long serialVersionUID = 1L;

    private int argc_;  // this contains the number of argumnets in the
                        // original command line

    private List<String> noargs;  // this contains the arguments pssed that are
                                  // not associated with an option.

    /**
     * This constructs a CommandLineParser from an argument list. This
     * constructor can handle a null argument. null will result in an
     * empty HashMap.
     */
    public CommandLineParser(final String[] args) {
        this(args, false);
    }

    public CommandLineParser(final String[] args, final boolean addSystemParams) {
        noargs = null;
        parse(args);

        if(addSystemParams) {
            final Properties sprop = System.getProperties();
            if(sprop != null && sprop.size() > 0) {
                for(final Object key: sprop.keySet()) {
                    if(key != null)
                        put((String)key, (String)sprop.get(key));
                }
            }
        }
    }

    public int getTotalArgCount() {
        return argc_;
    }

    public int getOptionCount() {
        return size();
    }

    public List<String> getNonOptionArgs() {
        return noargs;
    }

    /**
     * The following method will construct a string that contains
     * what was typed in. There is no guarantee as to the order. Also
     * options with no value will be translated as "-option true" and
     * unoptionedvalues will appear the same way. This may be fixed
     * in the future.
     */
    public String reconstructCommandLine() {
        final Set<Map.Entry<String, String>> s = entrySet();
        final Iterator<Map.Entry<String, String>> iter = s.iterator();
        String ret = "";

        if(noargs != null)
            for(int i = 0; i < noargs.size(); i++)
            ret = ret + " " + noargs.get(i);

        while(iter.hasNext()) {
            final Map.Entry<String, String> nv = iter.next();
            final String key = (nv.getKey());

            if(key != null) {
                ret = ret + " -" + key;
                final String val = (nv.getValue());
                if(val != null && val.charAt(0) != '\0') {
                    ret = ret + " " + val;
                }
            }
        }

        return ret;
    }

    public String getProperty(final String key) {
        return(this.get(key));
    }

    private void parse(final String[] args) {
        if(args == null)
            return;

        argc_ = args.length;

        for(int i = 0; i < argc_; i++) {
            String cur = args[i];
            cur = cur.trim();

            String val = null;

            if(cur.charAt(0) == '-' /* || cur.charAt(0) == '/' */) {
                // strip the dash off of cur
                if(cur.length() > 1)
                    cur = cur.substring(1);

                // val is the parameter
                if(i + 1 < argc_) {
                    val = args[i + 1];
                    if(val.charAt(0) == '-' /* || val.charAt(0) == '/' */)
                        // then this is not a val ... its the next selection...
                        val = null;
                    else
                        // increment
                        i++;
                } else
                    val = null;
            } else {
                if(noargs == null)
                    noargs = new ArrayList<String>();

                noargs.add(cur);
                cur = null;
            }

            if(cur != null)
                put(cur, val == null ? "true" : val);
        }

    }
}
