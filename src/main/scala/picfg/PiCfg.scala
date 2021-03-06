package picfg

import java.awt._
import javax.swing._
import javax.swing.table.TableCellEditor

import picfg.config._
import picfg.elements._
import picfg.log._
import sodium.CellSink

import scala.concurrent.duration._

object PiCfg extends App with LogSupport {

  // FIXME: app configuration
  val piUser = "pi"
  val piPwd = "pi"

  val broadcastPort = 33333
  val scanNetworkDur = 5.seconds
  //


  // build the view and startup
  val view = new MainView()
  view.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
  view.setVisible(true)


  class MainView extends JFrame {
    setLayout(new BorderLayout())

    // main tabs
    val tabs = new TabsPanel()
    add(tabs, BorderLayout.CENTER)

    tabs.addTab(new ScanTab(tabs))

    // logs
    val logList = new LogList
    add(logList, BorderLayout.SOUTH)
    onLogMsg(msg => logList.addLog(msg))

    pack()
  }


  class ScanTab(tabs: TabsPanel) extends Tab("Scan Network") {
    // scan result
    val table = new ScanResultTable()
    table.rowSelected.map { pi =>
      val remote = new Remote(pi, piUser, piPwd)
      remote.fetch(Configurations).left.map { e =>
        logError("unable to fetch remote config", e)
        e.printStackTrace()
      }.right.map { configs =>
        tabs.addTab(new ConfigTab(pi, remote, configs), setActive = true)
      }
    }


    add(new JScrollPane(table), BorderLayout.CENTER)

    // scan button
    add(new Btn("Scan", "network.png").onClick { btn =>
      logInfo("Start scan")
      btn.setEnabled(false)
      import scala.concurrent.ExecutionContext.Implicits.global
      table.clearRows()
      Scanner.scanNetwork(broadcastPort, scanNetworkDur).onComplete { _ =>
        logInfo("Scan done")
        btn.setEnabled(true)
      }
    }, BorderLayout.NORTH)


    class ScanResultTable extends Table(TableModel.fromStream(Scanner.findings, "Name", "Ip")) {
      override def isCellEditable(row: Int, column: Int): Boolean = false
    }

  }


  class ConfigTab(pi: Pi, remote: Remote, configs: Seq[Config]) extends Tab(pi.toString()) {
    val toolBar = add(new ToolBar(), BorderLayout.NORTH)

    val tabs = add(new TabsPanel(), BorderLayout.CENTER)

    val tablesAndTheirIsDirty: Seq[(ConfigTable, CellSink[Boolean])] = configs.map { config =>
      val table = new ConfigTable(config)
      val tab = tabs.addTab(new Tab(config.name) {
        add(new JScrollPane(table), BorderLayout.CENTER)
        override val isDirty = table.isDirty
      })

      (table, tab.isDirty)
    }


    override val isDirty = tablesAndTheirIsDirty.map(_._2.value).reduce(_ merge _).hold(false)


    toolBar.add(new Btn("Push", "push.png")).onClick { _ =>
      val dirtyTablesAndTheirIsDirty = tablesAndTheirIsDirty.filter(_._2.sample)
      remote.push(dirtyTablesAndTheirIsDirty.map(_._1.config)).left.map { e =>
        logError("unable to send config", e)
        e.printStackTrace()
      }.right.map { _ =>
        dirtyTablesAndTheirIsDirty.map(_._2.send(false))
      }
    }

    // close tab
    val btnClose = toolBar.add(new Btn("Close", "close.png"))
    val dismissDialog = new WarningDialog("Dismiss changes?", btnClose.clicked.gate(isDirty).map(_ => true))
    // close when (btnClose clicked AND state is clean) OR (yes clicked in the dismiss dialog)
    override val close = btnClose.clicked.gate(isDirty.map(!_)) merge dismissDialog.yesClicked


    class ConfigTable(val config: Config) extends Table(TableModel.fromSeq(config.props.values.toSeq, "Name", "Value", "Description")) {
      setColumnColor(0, Color.LIGHT_GRAY)
      setColumnColor(2, Color.LIGHT_GRAY)

      override def isCellEditable(row: Int, column: Int): Boolean = column == 1

      override def getCellEditor(row: Int, column: Int): TableCellEditor = {
        val prop = model.getRow(row) match {
          case p: Prop => p
          case _ => throw new RuntimeException("not possible: table content are always 'Prop' values!")
        }

        // show a combobox with valid values if given
        if (prop.validValues.nonEmpty) {
          new DefaultCellEditor(new JComboBox(prop.validValues.toArray))
        } else {
          super.getCellEditor(row, column)
        }
      }


      override def setValueAt(aValue: scala.Any, row: Int, column: Int): Unit = {
        super.setValueAt(aValue, row, column)
        config.updateProp(getRows(row))
      }
    }

  }

}
