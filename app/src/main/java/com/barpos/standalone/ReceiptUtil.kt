package com.barpos.standalone

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a single-page PDF receipt as a thermal-printer-style 80mm slip (~226 pt wide).
 * Saves to app's external files dir and returns a content:// Uri for sharing.
 */
object ReceiptUtil {

    fun generateReceiptPdf(
        context: Context,
        receipt: ReceiptSummary,
        currency: String,
        businessName: String,
        businessAddress: String,
        businessPhone: String,
        businessTaxId: String,
        receiptFooter: String,
    ): Uri? {
        val pdf = PdfDocument()
        val pageWidth = 226   // ~80mm @ 72dpi
        // We will compute page height dynamically: simulate first, then re-render
        // Pass 1: measure
        val tmpInfo = PdfDocument.PageInfo.Builder(pageWidth, 2000, 1).create()
        val tmpPage = pdf.startPage(tmpInfo)
        val tmpCanvas = tmpPage.canvas
        val measuredHeight = drawReceipt(
            canvas = tmpCanvas, width = pageWidth,
            receipt = receipt, currency = currency,
            businessName = businessName, businessAddress = businessAddress,
            businessPhone = businessPhone, businessTaxId = businessTaxId,
            receiptFooter = receiptFooter, measureOnly = true,
        )
        pdf.finishPage(tmpPage)
        pdf.close()

        // Pass 2: real render
        val pdf2 = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(pageWidth, measuredHeight + 16, 1).create()
        val page = pdf2.startPage(info)
        drawReceipt(
            canvas = page.canvas, width = pageWidth,
            receipt = receipt, currency = currency,
            businessName = businessName, businessAddress = businessAddress,
            businessPhone = businessPhone, businessTaxId = businessTaxId,
            receiptFooter = receiptFooter, measureOnly = false,
        )
        pdf2.finishPage(page)

        // Save
        val dir = File(context.getExternalFilesDir(null), "receipts")
        if (!dir.exists()) dir.mkdirs()
        val fileName = "receipt_${receipt.txId}_${receipt.createdAt}.pdf"
        val file = File(dir, fileName)
        return try {
            FileOutputStream(file).use { pdf2.writeTo(it) }
            pdf2.close()
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            pdf2.close()
            null
        }
    }

    private fun drawReceipt(
        canvas: android.graphics.Canvas,
        width: Int,
        receipt: ReceiptSummary,
        currency: String,
        businessName: String,
        businessAddress: String,
        businessPhone: String,
        businessTaxId: String,
        receiptFooter: String,
        measureOnly: Boolean,
    ): Int {
        val rightPad = 8f
        val leftPad = 8f
        val contentWidth = width - rightPad - leftPad
        var y = 14f

        val titlePaint = Paint().apply {
            isAntiAlias = true; textSize = 14f; isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val normalPaint = Paint().apply {
            isAntiAlias = true; textSize = 9.5f
        }
        val smallPaint = Paint().apply {
            isAntiAlias = true; textSize = 8f
        }
        val centerPaint = Paint().apply {
            isAntiAlias = true; textSize = 9.5f; textAlign = Paint.Align.CENTER
        }
        val rightAlign = Paint().apply {
            isAntiAlias = true; textSize = 9.5f; textAlign = Paint.Align.RIGHT
        }
        val bigBoldPaint = Paint().apply {
            isAntiAlias = true; textSize = 12f; isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val midX = width / 2f

        // Business name
        canvas.drawText(businessName, midX, y, titlePaint)
        y += 16
        if (businessAddress.isNotBlank()) {
            canvas.drawText(businessAddress, midX, y, centerPaint)
            y += 12
        }
        if (businessPhone.isNotBlank()) {
            canvas.drawText("טל׳: $businessPhone", midX, y, centerPaint)
            y += 12
        }
        if (businessTaxId.isNotBlank()) {
            canvas.drawText("ע.מ./ח.פ.: $businessTaxId", midX, y, centerPaint)
            y += 12
        }
        y += 4
        drawDashedLine(canvas, leftPad, y, width - rightPad, smallPaint)
        y += 10

        // Date + receipt #
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("he", "IL"))
        val isRefund = receipt.paymentMethod.startsWith("refund")
        if (isRefund) {
            canvas.drawText("*** זיכוי ***", midX, y, titlePaint)
            y += 16
        }
        canvas.drawText("חשבונית #${receipt.txId}", width - rightPad, y, rightAlign)
        y += 12
        canvas.drawText(fmt.format(Date(receipt.createdAt)),
                        width - rightPad, y, rightAlign)
        y += 12
        canvas.drawText("עובד: ${receipt.employeeName}",
                        width - rightPad, y, rightAlign)
        y += 12
        drawDashedLine(canvas, leftPad, y, width - rightPad, smallPaint)
        y += 10

        // Lines
        receipt.lines.forEach { line ->
            // Line: "name           qty × price"
            val nameText = line.itemName
            val rightText = "${line.quantity} × ${formatMoneyShort(line.priceAtTime, currency)}"
            val totalText = formatMoneyShort(line.priceAtTime * line.quantity, currency)
            canvas.drawText(nameText, width - rightPad, y, rightAlign)
            y += 11
            canvas.drawText(rightText, width - rightPad, y, rightAlign)
            // Total on left side same row
            normalPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(totalText, leftPad, y, normalPaint)
            y += 14
        }

        y += 2
        drawDashedLine(canvas, leftPad, y, width - rightPad, smallPaint)
        y += 10

        // Totals
        drawKv(canvas, "ביניים", formatMoneyShort(receipt.subtotal, currency),
               leftPad, width - rightPad, y, normalPaint, rightAlign)
        y += 12
        if (receipt.tax > 0) {
            drawKv(canvas, "מע\"מ", formatMoneyShort(receipt.tax, currency),
                   leftPad, width - rightPad, y, normalPaint, rightAlign)
            y += 12
        }
        if (receipt.tip > 0) {
            drawKv(canvas, "טיפ", formatMoneyShort(receipt.tip, currency),
                   leftPad, width - rightPad, y, normalPaint, rightAlign)
            y += 12
        }
        // Total bold
        canvas.drawText("סה״כ", width - rightPad, y, bigBoldPaint)
        normalPaint.isFakeBoldText = true
        normalPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(formatMoneyShort(receipt.total, currency),
                        leftPad, y, normalPaint)
        normalPaint.isFakeBoldText = false
        y += 16

        // Payment method
        val methodLabel = when (receipt.paymentMethod) {
            "cash" -> "מזומן"
            "credit" -> "אשראי"
            "refund_cash" -> "החזר מזומן"
            "refund_credit" -> "החזר אשראי"
            else -> receipt.paymentMethod
        }
        drawKv(canvas, "תשלום", methodLabel,
               leftPad, width - rightPad, y, normalPaint, rightAlign)
        y += 12

        if (receipt.paymentMethod == "cash" && receipt.cashReceived > 0) {
            drawKv(canvas, "התקבל", formatMoneyShort(receipt.cashReceived, currency),
                   leftPad, width - rightPad, y, normalPaint, rightAlign)
            y += 12
            drawKv(canvas, "עודף", formatMoneyShort(receipt.changeGiven, currency),
                   leftPad, width - rightPad, y, normalPaint, rightAlign)
            y += 12
        }

        y += 8
        drawDashedLine(canvas, leftPad, y, width - rightPad, smallPaint)
        y += 14

        if (receiptFooter.isNotBlank()) {
            // Wrap footer text manually
            val maxLineChars = 30
            val words = receiptFooter.split(" ")
            val lines = mutableListOf<String>()
            var current = StringBuilder()
            for (w in words) {
                if (current.length + w.length + 1 > maxLineChars && current.isNotEmpty()) {
                    lines.add(current.toString().trim())
                    current = StringBuilder()
                }
                current.append(w).append(" ")
            }
            if (current.isNotEmpty()) lines.add(current.toString().trim())
            lines.forEach { ln ->
                canvas.drawText(ln, midX, y, centerPaint)
                y += 12
            }
            y += 6
        }

        return y.toInt()
    }

    private fun drawDashedLine(canvas: android.graphics.Canvas, x1: Float, y: Float, x2: Float, paint: Paint) {
        val seg = 4f
        var x = x1
        while (x < x2) {
            canvas.drawLine(x, y, (x + seg).coerceAtMost(x2), y, paint)
            x += seg * 2
        }
    }

    private fun drawKv(
        canvas: android.graphics.Canvas,
        key: String, value: String,
        leftX: Float, rightX: Float, y: Float,
        leftPaint: Paint, rightPaint: Paint,
    ) {
        // key on right (Hebrew RTL), value on left
        rightPaint.isFakeBoldText = false
        canvas.drawText(key, rightX, y, rightPaint)
        leftPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(value, leftX, y, leftPaint)
    }

    private fun formatMoneyShort(d: Double, currency: String): String {
        return "$currency%.2f".format(d)
    }

    fun shareReceipt(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "שתף חשבונית")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
