package org.basex.test.inex;

import static org.basex.core.Text.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.basex.core.BaseXException;
import org.basex.core.Context;
import org.basex.core.Prop;
import org.basex.core.cmd.Close;
import org.basex.core.cmd.List;
import org.basex.core.cmd.Open;
import org.basex.core.cmd.Set;
import org.basex.core.cmd.XQuery;
import org.basex.server.ClientSession;
import org.basex.io.PrintOutput;
import org.basex.util.Args;
import org.basex.util.Performance;
import org.basex.util.StringList;
import org.basex.util.Util;

/**
 * Simple word frequency collector for INEXDBtests.
 *
 * @author BaseX Team 2005-11, BSD License
 * @author Christian Gruen
 * @author Sebastian Gath
 */
public final class InexTFCalc {
  /** Words file. */
  static final String WORDS = "words";
  /** Query file. */
  static final String WORDSF = "words.freq";
  /** Database prefix. */
  static final String DBPREFIX = "inex";

  /** Database context. */
  private final Context ctx = new Context();
  /** Databases. */
  private final StringList databases;
  /** Container for qtimes and results. */
  private final PrintOutput res = new PrintOutput(WORDSF);

  /** Session. */
  private ClientSession session;
  /** Queries. */
  private StringList words;
  /** Frequency of each word. */
  private int[] freq;

  /**
   * Main test method.
   * @param args command-line arguments
   * @throws Exception exception
   */
  public static void main(final String[] args) throws Exception {
    new InexTFCalc(args);
  }

  /**
   * Default constructor.
   * @param args command-line arguments
   * @throws Exception exception
   */
  private InexTFCalc(final String[] args) throws Exception {
    final Performance p = new Performance();
    Util.outln(Util.name(this));
    databases = new StringList();

    if(!parseArguments(args)) return;

    // cache queries
    final BufferedReader br = new BufferedReader(new FileReader(WORDS));
    words = new StringList();
    String l;
    while((l = br.readLine()) != null)
        words.add(l);
    br.close();

    freq = new int[words.size()];

    // cache database names
    if(databases.size() == 0)
      for(final String s : List.list(ctx))
        if(s.startsWith(DBPREFIX)) databases.add(s);

    Util.outln("=> % words on % databases: time in ms\n",
        words.size(), databases.size());

    // run test
    test();

    for(int i = 0; i < freq.length; ++i)
      res.println(words.get(i) + ";" + freq[i]);
    res.close();

    Util.outln("Total Time: " + p);
  }

  /**
   * Parses the command line arguments.
   * @param args command-line arguments
   * @return true if all arguments have been correctly parsed
   */
  private boolean parseArguments(final String[] args) {
    final Args arg = new Args(args, this, " [options]" + NL +
      "  -d database");
    while(arg.more()) {
      if(arg.dash()) {
        final char ca = arg.next();
        if(ca == 'd') {
          databases.add(arg.string());
        } else {
          arg.check(false);
        }
      }
    }
    if(!arg.finish()) return false;

    try {
      session = new ClientSession(ctx, ADMIN, ADMIN);
      session.execute(new Set(Prop.QUERYINFO, false));
      return true;
    } catch(final Exception ex) {
      Util.errln("Please run BaseXServer for using server mode.");
      Util.stack(ex);
      return false;
    }
  }

  /**
   * Second test, opening each databases before running the queries.
   * @throws Exception exception
   */
  private void test() throws Exception {
    // loop through all databases
    for(int d = 0; d < databases.size(); ++d) {
      // open database and loop through all queries
      session.execute(new Open(databases.get(d)));
      for(int q = 0; q < words.size(); ++q) {
        query(d, q);
      }
      session.execute(new Close());
    }
  }

  /**
   * Performs a single query.
   * @param db database offset
   * @param qu query offset
   */
  private void query(final int db, final int qu) {
    try {
      session.execute(new XQuery(
          "distinct-values((for $i in //*[text() contains text \""  +
          words.get(qu) + "\"] return base-uri($i)))"));

      final String str = session.info();
      final String items = find(str, "Results   : ([0-9]+) Item");

      // output result
      Util.outln("Query % on %: % items", qu + 1, databases.get(db), items);
      final int n = Integer.parseInt(items);
      if(n > 0) freq[qu] += n;
    } catch(final BaseXException ex) {
      Util.outln(session.info());
    }
  }

  /**
   * Finds a string in the specified pattern.
   * @param str input string
   * @param pat regular pattern
   * @return resulting string
   */
  private String find(final String str, final String pat) {
    final Matcher m = Pattern.compile(pat).matcher(str);
    return m.find() ? m.group(1) : "";
  }
}
