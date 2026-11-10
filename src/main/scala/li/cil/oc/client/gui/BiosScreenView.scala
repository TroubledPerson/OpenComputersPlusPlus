package li.cil.oc.client.gui

import li.cil.oc.Localization
import net.minecraft.client.gui.{FontRenderer,Gui}
import net.minecraft.client.renderer.GlStateManager
import li.cil.oc.plusplus.{BiosReport,OpticalAudioDiagnostics}

/** Shared projected/focused UI. Only fixed localized labels and bounded numeric data. */
object BiosScreenView {
  def draw(font: FontRenderer,x: Double,y: Double,width: Double,height: Double,on: Boolean,page: Int,selection: Int,diagnostic: Int): Unit =
    draw(font,x,y,width,height,on,page,selection,diagnostic,BiosReport.empty())
  def draw(font: FontRenderer,x: Double,y: Double,width: Double,height: Double,on: Boolean,page: Int,selection: Int,diagnostic: Int,report: Array[Int]): Unit = {
    val info=BiosReport.normalize(report)
    GlStateManager.pushMatrix()
    try{
      GlStateManager.translate(x,y,0)
      Gui.drawRect(0,0,width.toInt,height.toInt,0xFF05080D)
      if(!on)return
      val scale=math.min(width/320.0,height/200.0)
      def local(key: String)=Localization.localizeImmediately(key)
      def text(raw: String,cy: Double,color: Int=0xEEEEEE): Unit = {
        GlStateManager.pushMatrix()
        try{
          val lineScale=math.min(scale,width*0.9/math.max(1,font.getStringWidth(raw)))
          GlStateManager.translate(width/2,height*cy,0.04);GlStateManager.scale(lineScale,lineScale,1)
          val clipped=font.trimStringToWidth(raw,math.max(1,(width*0.9/lineScale).toInt))
          font.drawString(clipped,-font.getStringWidth(clipped)/2,-4,color)
        }finally{GlStateManager.popMatrix()}
      }
      def label(key: String,cy: Double,color: Int=0xEEEEEE): Unit=text(local(key),cy,color)
      def row(key: String,values: Seq[Int],cy: Double): Unit=text(local(key)+": "+values.mkString(" / "),cy)
      def result(code: Int)=local(if(code==1)"ocpp.superbios.pass" else if(code==2)"ocpp.superbios.fail" else "ocpp.superbios.notRun")
      def button(key: String,top: Double,bottom: Double,selected: Boolean=false,suffix: String=""): Unit = {
        GlStateManager.pushMatrix()
        try{
          GlStateManager.translate(0,0,0.02)
          Gui.drawRect((width*0.1).toInt,(height*top).toInt,(width*0.9).toInt,(height*bottom).toInt,if(selected)0xFF284D79 else 0xFF17283E)
        }finally{GlStateManager.popMatrix()}
        text(local(key)+suffix,(top+bottom)/2)
      }
      label("ocpp.superbios.title",0.10,0xFFFFFF)
      page match {
        case 0 =>
          for((key,row)<-Seq("ocpp.superbios.library","ocpp.superbios.system","ocpp.superbios.diagnostics").zipWithIndex)
            button(key,0.25+row*0.16,0.37+row*0.16,selection==row)
          label("ocpp.superbios.postPassed",0.79,0x88DD99)
          label("ocpp.superbios.biosOnly",0.89,0xAAB5C4)
        case 1 =>
          if(info(BiosReport.STATUS)!=1)label("ocpp.superbios.snapshotUnavailable",0.24,0xE0AA88)
          else row("ocpp.superbios.nodesComponents",Seq(info(BiosReport.NODES),info(BiosReport.COMPONENTS)),0.24)
          text(local("ocpp.superbios.screenGeometry")+": "+info(BiosReport.SCREENS)+" / "+info(BiosReport.COLUMNS)+" x "+info(BiosReport.ROWS),0.35)
          row("ocpp.superbios.gpuModemInternet",Seq(info(BiosReport.GPU),info(BiosReport.MODEM),info(BiosReport.INTERNET)),0.46)
          row("ocpp.superbios.dataSoundOther",Seq(info(BiosReport.DATA),info(BiosReport.SOUND),info(BiosReport.OTHER)),0.57)
          label("ocpp.superbios.statusOffline",0.68,0xAAB5C4);label("ocpp.superbios.guestUnavailable",0.79,0xAAB5C4)
          button("ocpp.superbios.back",0.84,0.96)
        case 2 =>
          label("ocpp.superbios.storageScope",0.17,0xAAB5C4)
          row("ocpp.superbios.fsCdRaid",Seq(info(BiosReport.FILESYSTEMS),info(BiosReport.CD_DRIVES),info(BiosReport.RAIDS)),0.28)
          row("ocpp.superbios.mediaReadOnly",Seq(info(BiosReport.MEDIA),info(BiosReport.READ_ONLY)),0.39)
          row("ocpp.superbios.quotaKiB",Seq(info(BiosReport.CAPACITY_KIB)),0.50)
          row("ocpp.superbios.quotaUnknown",Seq(info(BiosReport.QUOTA_UNKNOWN)),0.61)
          label("ocpp.superbios.noRuntime",0.71,0xAAB5C4);label("ocpp.superbios.noGuest",0.80,0xAAB5C4)
          button("ocpp.superbios.back",0.84,0.96)
        case 3 =>
          if(info(BiosReport.INPUT_TEST)==1){
            GlStateManager.pushMatrix()
            try{GlStateManager.translate(0,0,0.02)
              for((color,i)<-Seq(0xFFFF5555,0xFF55FF55,0xFF5555FF).zipWithIndex)
                Gui.drawRect((width*(0.1+i*0.27)).toInt,(height*0.13).toInt,(width*(0.35+i*0.27)).toInt,(height*0.17).toInt,color)
            }finally{GlStateManager.popMatrix()}
          }
          button("ocpp.superbios.scratch",0.20,0.30,suffix=": "+result(info(BiosReport.MEMORY_TEST)))
          button("ocpp.superbios.inputModel",0.32,0.42,suffix=": "+result(info(BiosReport.INPUT_TEST)))
          button("ocpp.superbios.runMock",0.45,0.65,suffix=": "+result(if(diagnostic==1)1 else 0))
          button("ocpp.superbios.audioEvidence",0.68,0.80);button("ocpp.superbios.back",0.84,0.96)
        case 4 =>
          val client=OpticalAudioDiagnostics.snapshot()
          def server(stage: Int)=info(BiosReport.OPTICAL_BASE+stage)
          label("ocpp.superbios.audioScope",0.17,0xAAB5C4)
          row("ocpp.superbios.audioEnabled",Seq(info(BiosReport.OPTICAL_ENABLED),if(OpticalAudioDiagnostics.enabled)1 else 0),0.27)
          row("ocpp.superbios.audioAccess",Seq(server(OpticalAudioDiagnostics.Read),server(OpticalAudioDiagnostics.Seek),server(OpticalAudioDiagnostics.Queued),server(OpticalAudioDiagnostics.NoMedia)),0.36)
          row("ocpp.superbios.audioDispatch",Seq(server(OpticalAudioDiagnostics.ServerRead),server(OpticalAudioDiagnostics.ServerStop)),0.45)
          row("ocpp.superbios.audioDecode",Seq(client(OpticalAudioDiagnostics.PacketDecoded),client(OpticalAudioDiagnostics.Received)),0.54)
          row("ocpp.superbios.audioPlay",Seq(client(OpticalAudioDiagnostics.Played),client(OpticalAudioDiagnostics.Refreshed)),0.63)
          row("ocpp.superbios.audioCancel",Seq(client(OpticalAudioDiagnostics.Cancelled),client(OpticalAudioDiagnostics.PacketRejected)),0.72)
          row("ocpp.superbios.audioGates",Seq(client(OpticalAudioDiagnostics.NoWorld),client(OpticalAudioDiagnostics.Muted),client(OpticalAudioDiagnostics.NoDrive),client(OpticalAudioDiagnostics.VoiceLimit)),0.80)
          button("ocpp.superbios.back",0.84,0.96)
        case _ =>
      }
    }finally{GlStateManager.popMatrix()}
  }
}
