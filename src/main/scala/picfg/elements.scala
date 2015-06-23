package picfg

import java.awt.event._
import java.awt.{BorderLayout, Font, Color, Component}
import java.beans.{PropertyChangeEvent, PropertyChangeListener}
import java.io.File
import java.lang.Integer
import java.net.URL
import java.text.SimpleDateFormat
import java.util
import java.util.Date
import javax.swing._
import javax.swing.event.{TableModelEvent, TableModelListener}
import javax.swing.table.{DefaultTableCellRenderer, DefaultTableModel}

import picfg.config.{Prop, Config}
import sodium.{Cell, CellSink, Stream, StreamSink}

import scala.io.Source
import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror


object elements {

  object Icon {
    def apply(name: String) = {
      val url = Option(ClassLoader.getSystemResource(name)).getOrElse {
        val pwd = System.getProperty("user.dir")
        new URL(s"file://${pwd}/src/main/resources/${name}")
      }
      new ImageIcon(url)
    }
  }

  trait Element extends JComponent {
    def add[A <: Element](e: A): A = {
      super.add(e)
      e
    }

    def add[A <: Element](e: A, constraints: AnyRef): A = {
      super.add(e, constraints)
      e
    }
  }


  case class Btn(label: String, icon: Option[String] = None) extends JButton(label) with Element {
    def this(label: String, icon: String) = this(label, Some(icon))

    // set the icon
    icon.foreach(p => setIcon(Icon(p)))

    // event handler
    val clicked = new StreamSink[Unit]
    addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = {
        clicked.send(())
      }
    })

    def onClick(f: Btn => Unit): Btn = {
      clicked.map { _ =>
        f(this)
      }
      this
    }

  }


  case class Tab(title: String) extends Element {
    setLayout(new BorderLayout)
    val isDirty: Cell[Boolean] = new Cell(false)
    val close: Stream[Unit] = new Stream()
  }

  case class TabsPanel(private val initialTabs: Tab*) extends JTabbedPane with Element {
    // add initial tabs
    initialTabs.foreach(tab => addTab(tab))

    def addTab[A <: Tab](tab: A, setActive: Boolean = false): A = {
      addTab(tab.title, tab)

      // keep it as 'def', to fetch the actual index at runtime
      // if we have: <TAB1 (idx: 0)>, <TAB2 (idx: 1)> and we delete TAB1, then
      // TAB2 become index: 0
      def idx = indexOfComponent(tab)
      if(setActive){
        setSelectedIndex(idx)
      }

      // handle close
      tab.close.map(_ => removeTabAt(idx))

      // handle isDirty
      tab.isDirty.value.map { isDirty =>
        if (isDirty) setIconAt(idx, Icon("tab-dirty.png"))
        else setIconAt(idx, null) // java api!!!
      }

      tab
    }

  }


  object TableModel {
    def fromSeq[A <: Product : TypeTag](seq: Seq[A], colNames: String*): TableModel[A] = {
      new TableModel(seq, colNames)
    }

    def fromProduct[A <: Product : TypeTag](p: Product, colNames: String*): TableModel[A] = {
      val seq = p.productIterator.toSeq.asInstanceOf[Seq[A]]
      fromSeq(seq, colNames: _*)
    }

    def fromStream[A <: Product : TypeTag](stream: Stream[A], colNames: String*): TableModel[A] = {
      val model = new TableModel(Vector.empty[A], colNames)

      // react on new rows
      stream.map { row =>
        model.addRow(row)
      }

      model
    }
  }


  case class TableModel[A <: Product : TypeTag](private var rows: Seq[A], private val colNames: Seq[String]) extends DefaultTableModel {

    import scala.collection.JavaConverters._

    setDataVector(new java.util.Vector(rows.map(_.asJavaVector).asJava), new util.Vector(colNames.asJava))

    val isDirty = new CellSink(false)

    addTableModelListener(new TableModelListener {
      override def tableChanged(e: TableModelEvent): Unit = {
        // e.getColumn return -1 when new data was added
        if (e.getColumn >= 0)
          isDirty.send(true)
      }
    })

    def getRows: Seq[A] = rows

    def getRow(idx: Int): A = rows(idx)

    def addRow(row: A): Unit = {
      // internal, typesave
      rows :+= row

      // table model - typeless
      super.addRow(row.asJavaVector)
    }

    def clearRows(): Unit = {
      while (getRowCount > 0) {
        removeRow(0)
      }
      rows = Vector.empty[A]
      isDirty.send(false)
    }


    override def setValueAt(aValue: scala.Any, row: Int, column: Int): Unit = {
      // update the typesave model - internal model
      val original = rows(row).productIterator.toList
      val updated = original.updated(column, aValue)

      val m = runtimeMirror(getClass.getClassLoader)
      val classMirror = currentMirror.reflectClass(typeOf[A].typeSymbol.asClass)
      val ctor = typeOf[A].decl(termNames.CONSTRUCTOR).asMethod
      val newCopy = classMirror.reflectConstructor(ctor).apply(updated: _*).asInstanceOf[A]
      rows = rows.updated(row, newCopy)

      // update the typeless model - table model
      super.setValueAt(aValue, row, column)
    }


  }


  case class Table[A <: Product](val model: TableModel[A]) extends JTable with Element {
    setAutoCreateRowSorter(true)
    setModel(model)

    val isDirty: CellSink[Boolean] = model.isDirty


    // row selection
    private val rowSelected_ = new StreamSink[A]
    addMouseListener(new MouseListener {
      override def mouseClicked(e: MouseEvent): Unit = {
        if (e.getClickCount >= 2) {
          rowSelected_.send(model.getRow(getSelectedRow))
        }

      }

      // ignore
      override def mouseExited(e: MouseEvent): Unit = {}

      override def mouseEntered(e: MouseEvent): Unit = {}

      override def mousePressed(e: MouseEvent): Unit = {}

      override def mouseReleased(e: MouseEvent): Unit = {}
    })


    // public accessor for rowSelected_
    val rowSelected: Stream[A] = rowSelected_

    def setColumnColor(colIdx: Int, color: Color): Unit = {
      // what?!?!?!
      getColumnModel.getColumn(colIdx).setCellRenderer(new DefaultTableCellRenderer {
        override def getTableCellRendererComponent(table: JTable, value: scala.Any, isSelected: Boolean,
                                                   hasFocus: Boolean, row: Int, column: Int): Component = {
          val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
          comp.setBackground(color)
          comp
        }
      })
    }

    def clearRows(): Unit = model.clearRows()


    def getRows = model.getRows

  }


  class ToolBar extends JToolBar with Element


  class LogList extends JScrollPane with Element {
    private val model = new DefaultListModel[Log]
    private val jList = new JList(model)
    setViewportView(jList)

    def append(l: Log): Unit = {
      val (msg, color) = l match {
        case Info(msg) => (msg, Color.BLACK)
        case Error(msg, e) => (msg, Color.RED)
      }

      def prefixWithTS(s: String): String = {
        val sdf = new SimpleDateFormat("HH:mm:ss")
        s"${sdf.format(new Date())} | ${s}"
      }

      val label = new JLabel(prefixWithTS(msg))
      label.setForeground(color)
      model.addElement(l)
    }


    //FIXME: scrolling per mouse not possible!
    getVerticalScrollBar.addAdjustmentListener(new AdjustmentListener {
      override def adjustmentValueChanged(e: AdjustmentEvent): Unit = {
        e.getAdjustable.setValue(e.getAdjustable.getMaximum)
      }
    })

  }

  case class WarningDialog(title: String, fYes: JDialog => Unit, fNo: JDialog => Unit, show: sodium.Stream[Boolean]) {
    def this(title: String, fYes: JDialog => Unit, fNo: JDialog => Unit) =
      this(title, fYes, fNo, new Cell(true).value)

    def this(title: String, show: sodium.Stream[Boolean]) =
      this(title, _ => (), _ => (), show)

    private val yesClicked_ = new StreamSink[Unit]
    private val noClicked_ = new StreamSink[Unit]

    private val dialog = new JDialog()
    dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE)
    dialog.setLocationRelativeTo(null)

    show.map { setVisible =>

      if (setVisible) {
        val optionPane = new JOptionPane(title, JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION)
        optionPane.addPropertyChangeListener("value", new PropertyChangeListener {
          override def propertyChange(evt: PropertyChangeEvent): Unit = evt.getNewValue.asInstanceOf[Integer].intValue match {
            case JOptionPane.YES_OPTION =>
              yesClicked_.send(())
              fYes(dialog)
              dialog.setVisible(false)
            case JOptionPane.NO_OPTION =>
              noClicked_.send(())
              fNo(dialog)
              dialog.setVisible(false)
          }
        })
        dialog.setContentPane(optionPane)
        dialog.pack()
      }
      dialog.setVisible(setVisible)
    }

    val yesClicked: Stream[Unit] = yesClicked_
    val noClicked: Stream[Unit] = noClicked_
  }

}
