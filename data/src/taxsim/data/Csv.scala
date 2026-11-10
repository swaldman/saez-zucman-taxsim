package taxsim.data

/** A minimal RFC-4180-ish CSV reader (quoted fields, doubled quotes), sufficient for our datasets. */
final case class Csv(header: IndexedSeq[String], rows: IndexedSeq[IndexedSeq[String]]):
  private val index: Map[String, Int] = header.zipWithIndex.toMap

  def has(column: String): Boolean = index.contains(column)

  def column(name: String): IndexedSeq[String] =
    val i = index.getOrElse(name, throw new NoSuchElementException(s"no CSV column '$name' (have: ${header.mkString(", ")})"))
    rows.map(r => if i < r.length then r(i) else "")

  /** Numeric column; blank cells become `blank`. */
  def doubles(name: String, blank: Double = 0.0): IndexedSeq[Double] =
    column(name).map(s => if s.trim.isEmpty then blank else s.trim.toDouble)

  /** Numeric column, or zeros when the column is absent. */
  def doublesOrZero(name: String): IndexedSeq[Double] =
    if has(name) then doubles(name) else IndexedSeq.fill(rows.length)(0.0)

object Csv:
  def parse(text: String): Csv =
    val records = parseRecords(text).filter(r => !(r.length == 1 && r.head.isEmpty))
    require(records.nonEmpty, "empty CSV")
    Csv(records.head, records.tail)

  private def parseRecords(text: String): IndexedSeq[IndexedSeq[String]] =
    val out = IndexedSeq.newBuilder[IndexedSeq[String]]
    var row = IndexedSeq.newBuilder[String]
    val field = new StringBuilder
    var inQuotes = false
    var i = 0
    def endField(): Unit = { row += field.toString; field.clear() }
    def endRow(): Unit = { endField(); out += row.result(); row = IndexedSeq.newBuilder[String] }
    while i < text.length do
      val c = text.charAt(i)
      if inQuotes then
        if c == '"' then
          if i + 1 < text.length && text.charAt(i + 1) == '"' then { field += '"'; i += 1 }
          else inQuotes = false
        else field += c
      else
        c match
          case '"'  => inQuotes = true
          case ','  => endField()
          case '\n' => endRow()
          case '\r' => ()
          case _    => field += c
      i += 1
    if field.nonEmpty || text.nonEmpty && text.last != '\n' then endRow()
    out.result()
