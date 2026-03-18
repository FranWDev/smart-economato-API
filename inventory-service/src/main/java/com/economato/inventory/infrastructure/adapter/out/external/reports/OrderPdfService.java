package com.economato.inventory.infrastructure.adapter.out.external.reports;

import com.economato.inventory.domain.model.OrderStatus;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.economato.inventory.application.dto.response.OrderDetailResponseDTO;
import com.economato.inventory.application.dto.response.OrderResponseDTO;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

@Service
public class OrderPdfService {

	private static final Color PRIMARY_COLOR = new DeviceRgb(184, 75, 68);
	private static final Color SECONDARY_COLOR = new DeviceRgb(160, 61, 55);
	private static final Color BORDER_COLOR = new DeviceRgb(229, 231, 235);
	private static final Color TEXT_DARK = new DeviceRgb(51, 51, 51);
	private static final Color TEXT_GRAY = new DeviceRgb(107, 114, 128);
	private static final Color SECTION_TITLE_COLOR = new DeviceRgb(55, 65, 81);
	private static final Color ROW_HOVER_BG = new DeviceRgb(243, 244, 246);

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final I18nService i18nService;

	public OrderPdfService(I18nService i18nService) {
		this.i18nService = i18nService;
	}

	public byte[] generateOrderPdf(OrderResponseDTO order) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			PdfWriter writer = new PdfWriter(baos);
			PdfDocument pdfDoc = new PdfDocument(writer);

			PdfFont footerFont = PdfFontFactory.createFont("Helvetica");
			pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new FooterEventHandler(footerFont));

			try (Document document = new Document(pdfDoc, PageSize.A4)) {
				document.setMargins(40, 40, 50, 40);

				PdfFont regularFont = footerFont;
				PdfFont boldFont = PdfFontFactory.createFont("Helvetica-Bold");

				addHeader(document, order, boldFont);
				addOrderInfoSection(document, order, boldFont, regularFont);
				addProductsTable(document, order.getDetails(), boldFont, regularFont);
				addTotalBanner(document, order.getTotalPrice(), boldFont);
			}
			return baos.toByteArray();
		} catch (Exception e) {
			throw new RuntimeException(i18nService.getMessage(MessageKey.ERROR_REPORT_ORDER_PDF_GENERATION, new Object[] { order.getId() }), e);
		}
	}

	private void addHeader(Document document, OrderResponseDTO order, PdfFont boldFont) {
		Table headerTable = new Table(UnitValue.createPercentArray(new float[] { 3, 1 }))
				.setWidth(UnitValue.createPercentValue(100))
				.setBackgroundColor(PRIMARY_COLOR)
				.setMarginBottom(0);

		Cell titleCell = new Cell()
				.add(new Paragraph(sanitizePdfText(i18nService.getMessage(MessageKey.REPORT_ORDER_TITLE_PREFIX, new Object[] { order.getId() })))
						.setFont(boldFont)
						.setFontSize(20)
						.setFontColor(ColorConstants.WHITE))
				.setBorder(Border.NO_BORDER)
				.setPadding(20)
				.setVerticalAlignment(VerticalAlignment.MIDDLE);

		Cell statusCell = new Cell()
				.add(new Paragraph(sanitizePdfText(translateStatus(order.getStatus())))
						.setFont(boldFont)
						.setFontSize(9)
						.setFontColor(ColorConstants.WHITE)
						.setTextAlignment(TextAlignment.CENTER))
				.setBorder(Border.NO_BORDER)
				.setPadding(20)
				.setVerticalAlignment(VerticalAlignment.MIDDLE);

		headerTable.addCell(titleCell);
		headerTable.addCell(statusCell);
		document.add(headerTable);

		Paragraph accent = new Paragraph("")
				.setBackgroundColor(SECONDARY_COLOR)
				.setPaddingTop(3)
				.setMarginTop(0)
				.setMarginBottom(24);
		document.add(accent);
	}

	private void addOrderInfoSection(Document document, OrderResponseDTO order, PdfFont boldFont,
			PdfFont regularFont) {
		addSectionTitle(document, i18nService.getMessage(MessageKey.REPORT_SECTION_ORDER_INFO), boldFont);

		Table infoTable = new Table(UnitValue.createPercentArray(new float[] { 1, 2, 1, 2 }))
				.setWidth(UnitValue.createPercentValue(100))
				.setMarginBottom(24);

		addInfoPair(infoTable, i18nService.getMessage(MessageKey.REPORT_LABEL_USER), sanitizePdfText(order.getUserName()), boldFont, regularFont);
		addInfoPair(infoTable, i18nService.getMessage(MessageKey.REPORT_LABEL_STATUS), translateStatus(order.getStatus()), boldFont, regularFont);
		addInfoPair(infoTable, i18nService.getMessage(MessageKey.REPORT_LABEL_DATE),
				order.getOrderDate() == null ? "" : order.getOrderDate().format(DATE_FORMAT),
				boldFont, regularFont);
		addInfoPair(infoTable, i18nService.getMessage(MessageKey.REPORT_LABEL_PRODUCTS_COUNT),
				String.valueOf(order.getDetails() == null ? 0 : order.getDetails().size()),
				boldFont, regularFont);

		document.add(infoTable);
	}

	private void addSectionTitle(Document document, String title, PdfFont boldFont) {
		Paragraph sectionTitle = new Paragraph(title)
				.setFont(boldFont)
				.setFontSize(14)
				.setFontColor(SECTION_TITLE_COLOR)
				.setMarginBottom(4)
				.setPaddingBottom(8)
				.setBorderBottom(new SolidBorder(PRIMARY_COLOR, 2));
		document.add(sectionTitle);
	}

	private void addInfoPair(Table table, String label, String value, PdfFont boldFont, PdfFont regularFont) {
		Cell labelCell = new Cell()
				.add(new Paragraph(label)
						.setFont(boldFont)
						.setFontSize(8)
						.setFontColor(TEXT_GRAY)
						.setCharacterSpacing(0.5f))
				.setBorder(Border.NO_BORDER)
				.setPaddingTop(8)
				.setPaddingBottom(4);

		Cell valueCell = new Cell()
				.add(new Paragraph(sanitizePdfText(value))
						.setFont(regularFont)
						.setFontSize(10)
						.setFontColor(TEXT_DARK))
				.setBorder(Border.NO_BORDER)
				.setPaddingTop(8)
				.setPaddingBottom(4);

		table.addCell(labelCell);
		table.addCell(valueCell);
	}

	private void addProductsTable(Document document, List<OrderDetailResponseDTO> details,
			PdfFont boldFont, PdfFont regularFont) {
		addSectionTitle(document, i18nService.getMessage(MessageKey.REPORT_SECTION_PRODUCTS_TITLE_PREFIX, new Object[] { (details == null ? 0 : details.size()) }), boldFont);

		Table table = new Table(UnitValue.createPercentArray(new float[] { 3, 1, 1, 1 }))
				.setWidth(UnitValue.createPercentValue(100))
				.setMarginTop(8)
				.setMarginBottom(8);

		table.addHeaderCell(createTableHeaderCell(i18nService.getMessage(MessageKey.REPORT_COLUMN_PRODUCT), boldFont));
		table.addHeaderCell(createTableHeaderCell(i18nService.getMessage(MessageKey.REPORT_COLUMN_QUANTITY), boldFont));
		table.addHeaderCell(createTableHeaderCell(i18nService.getMessage(MessageKey.REPORT_COLUMN_UNIT_PRICE), boldFont));
		table.addHeaderCell(createTableHeaderCell(i18nService.getMessage(MessageKey.REPORT_COLUMN_SUBTOTAL), boldFont));

		if (details != null) {
			for (int i = 0; i < details.size(); i++) {
				OrderDetailResponseDTO detail = details.get(i);
				Color rowBg = (i % 2 == 1) ? ROW_HOVER_BG : ColorConstants.WHITE;

				table.addCell(createTableDataCell(sanitizePdfText(detail.getProductName()), boldFont,
						rowBg, true));
				table.addCell(
						createTableDataCell(formatDecimal(detail.getQuantity()) + " " + i18nService.getMessage(MessageKey.GENERAL_UNIT_ABBREVIATION),
								regularFont, rowBg, false));
				table.addCell(createTableDataCell(formatCurrency(detail.getUnitPrice()), regularFont,
						rowBg, false));
				table.addCell(createTableDataCellAccent(formatCurrency(detail.getSubtotal()), boldFont,
						rowBg));
			}
		}

		document.add(table);
	}

	private Cell createTableHeaderCell(String text, PdfFont boldFont) {
		return new Cell()
				.add(new Paragraph(text)
						.setFont(boldFont)
						.setFontSize(9)
						.setFontColor(ColorConstants.WHITE)
						.setCharacterSpacing(0.5f))
				.setBackgroundColor(PRIMARY_COLOR)
				.setBorder(Border.NO_BORDER)
				.setPadding(12)
				.setTextAlignment(TextAlignment.LEFT);
	}

	private Cell createTableDataCell(String text, PdfFont font, Color bgColor, boolean isBold) {
		Paragraph p = new Paragraph(text)
				.setFont(font)
				.setFontSize(9)
				.setFontColor(isBold ? TEXT_DARK : TEXT_GRAY);

		return new Cell()
				.add(p)
				.setBackgroundColor(bgColor)
				.setBorder(Border.NO_BORDER)
				.setBorderBottom(new SolidBorder(BORDER_COLOR, 0.5f))
				.setPadding(10)
				.setTextAlignment(TextAlignment.LEFT);
	}

	private Cell createTableDataCellAccent(String text, PdfFont boldFont, Color bgColor) {
		return new Cell()
				.add(new Paragraph(text)
						.setFont(boldFont)
						.setFontSize(9)
						.setFontColor(PRIMARY_COLOR))
				.setBackgroundColor(bgColor)
				.setBorder(Border.NO_BORDER)
				.setBorderBottom(new SolidBorder(BORDER_COLOR, 0.5f))
				.setPadding(10)
				.setTextAlignment(TextAlignment.LEFT);
	}

	private void addTotalBanner(Document document, BigDecimal totalPrice, PdfFont boldFont) {
		Table totalTable = new Table(UnitValue.createPercentArray(new float[] { 1, 1 }))
				.setWidth(UnitValue.createPercentValue(100))
				.setBackgroundColor(PRIMARY_COLOR)
				.setMarginTop(12)
				.setMarginBottom(20);

		Cell labelCell = new Cell()
				.add(new Paragraph(i18nService.getMessage(MessageKey.REPORT_LABEL_TOTAL_ORDER))
						.setFont(boldFont)
						.setFontSize(14)
						.setFontColor(ColorConstants.WHITE))
				.setBorder(Border.NO_BORDER)
				.setPadding(16)
				.setVerticalAlignment(VerticalAlignment.MIDDLE);

		Cell valueCell = new Cell()
				.add(new Paragraph(formatCurrency(totalPrice))
						.setFont(boldFont)
						.setFontSize(22)
						.setFontColor(ColorConstants.WHITE)
						.setTextAlignment(TextAlignment.RIGHT))
				.setBorder(Border.NO_BORDER)
				.setPadding(16)
				.setVerticalAlignment(VerticalAlignment.MIDDLE);

		totalTable.addCell(labelCell);
		totalTable.addCell(valueCell);
		document.add(totalTable);
	}

	private class FooterEventHandler implements IEventHandler {
		private final PdfFont font;

		FooterEventHandler(PdfFont font) {
			this.font = font;
		}

		@Override
		public void handleEvent(Event event) {
			PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
			PdfDocument pdf = docEvent.getDocument();
			PdfPage page = docEvent.getPage();
			int pageNum = pdf.getPageNumber(page);
			float pageWidth = page.getPageSize().getWidth();

			try {
				PdfCanvas canvas = new PdfCanvas(page.newContentStreamAfter(), page.getResources(),
						pdf);

				try (Canvas c = new Canvas(canvas, new Rectangle(0, 18, pageWidth, 16))) {
					c.add(new Paragraph(String.valueOf(pageNum))
							.setFont(font)
							.setFontSize(8)
							.setFontColor(ColorConstants.BLACK)
							.setTextAlignment(TextAlignment.CENTER));
				}

				try (Canvas c = new Canvas(canvas, new Rectangle(0, 4, pageWidth, 14))) {
					c.add(new Paragraph(i18nService.getMessage(MessageKey.GENERAL_POWERED_BY))
							.setFont(font)
							.setFontSize(7)
							.setFontColor(TEXT_GRAY)
							.setTextAlignment(TextAlignment.CENTER));
				}

				canvas.release();
			} catch (Exception ignored) {
			}
		}
	}

	private String formatCurrency(BigDecimal value) {
		if (value == null) {
			return "0.00 \u20ac";
		}
		return String.format("%.2f \u20ac", value);
	}

	private String formatDecimal(BigDecimal value) {
		if (value == null) {
			return "0";
		}
		return String.format("%.2f", value);
	}

	private String sanitizePdfText(String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("[^\\u0009\\u000A\\u000D\\u0020-\\u00FF]", "");
	}

	private String translateStatus(OrderStatus status) {
		if (status == null) {
			return "";
		}
		return switch (status) {
			case CREATED -> i18nService.getMessage(MessageKey.STATUS_CREATED);
			case PENDING -> i18nService.getMessage(MessageKey.STATUS_PENDING);
			case REVIEW -> i18nService.getMessage(MessageKey.STATUS_REVIEW);
			case CONFIRMED -> i18nService.getMessage(MessageKey.STATUS_CONFIRMED);
			case INCOMPLETE -> i18nService.getMessage(MessageKey.STATUS_INCOMPLETE);
			case CANCELLED -> i18nService.getMessage(MessageKey.STATUS_CANCELLED);
		};
	}
}
