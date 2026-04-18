package com.economato.inventory.infrastructure.adapter.out.external.reports;

import com.economato.inventory.application.dto.response.WeeklyPlanResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotResponseDTO;
import com.economato.inventory.application.dto.response.WeeklyPlanSlotStudentResponseDTO;
import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

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
import com.itextpdf.layout.borders.DashedBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

@Service
public class WeeklyPlanPdfService {

	private static final Color PRIMARY_COLOR = new DeviceRgb(184, 75, 68);
	private static final Color SECONDARY_COLOR = new DeviceRgb(160, 61, 55);
	private static final Color BORDER_COLOR = new DeviceRgb(229, 231, 235);
	private static final Color TEXT_DARK = new DeviceRgb(51, 51, 51);
	private static final Color TEXT_GRAY = new DeviceRgb(107, 114, 128);
	private static final Color TABLE_HEADER_BG = new DeviceRgb(243, 244, 246);

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private final I18nService i18nService;

	public WeeklyPlanPdfService(I18nService i18nService) {
		this.i18nService = i18nService;
	}

	public byte[] generateWeeklyPlanPdf(WeeklyPlanResponseDTO plan) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			PdfWriter writer = new PdfWriter(baos);
			PdfDocument pdfDoc = new PdfDocument(writer);

			PdfFont footerFont = PdfFontFactory.createFont("Helvetica");
			pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new FooterEventHandler(footerFont));

			try (Document document = new Document(pdfDoc, PageSize.A4.rotate())) {
				document.setMargins(30, 30, 40, 30);

				PdfFont boldFont = PdfFontFactory.createFont("Helvetica-Bold");
				PdfFont regularFont = footerFont;

				addHeader(document, plan, boldFont, regularFont);
				addTimetable(document, plan, boldFont, regularFont);
			}
			return baos.toByteArray();
		} catch (Exception e) {
			throw new RuntimeException(i18nService.getMessage(MessageKey.ERROR_REPORT_WEEKLY_PLAN_PDF_GENERATION, new Object[] { plan.getId() }), e);
		}
	}

	private void addHeader(Document document, WeeklyPlanResponseDTO plan, PdfFont boldFont, PdfFont regularFont) {
		Table headerTable = new Table(UnitValue.createPercentArray(new float[] { 3, 1 }))
				.setWidth(UnitValue.createPercentValue(100))
				.setBackgroundColor(PRIMARY_COLOR)
				.setMarginBottom(0);

		Cell titleCell = new Cell()
				.add(new Paragraph(sanitizePdfText(i18nService.getMessage(MessageKey.REPORT_WEEKLY_PLAN_TITLE)))
						.setFont(boldFont)
						.setFontSize(20)
						.setFontColor(ColorConstants.WHITE))
				.add(new Paragraph(sanitizePdfText(i18nService.getMessage(MessageKey.REPORT_WEEKLY_PLAN_CHEF, new Object[]{plan.getChefName()})))
						.setFont(regularFont)
						.setFontSize(12)
						.setFontColor(ColorConstants.WHITE))
				.setBorder(Border.NO_BORDER)
				.setPadding(20)
				.setVerticalAlignment(VerticalAlignment.MIDDLE);

		String datesStr = "";
		if (plan.getWeekStartDate() != null && plan.getWeekEndDate() != null) {
			datesStr = i18nService.getMessage(MessageKey.REPORT_WEEKLY_PLAN_DATE_RANGE, 
					new Object[]{plan.getWeekStartDate().format(DATE_FORMAT), plan.getWeekEndDate().format(DATE_FORMAT)});
		}

		Cell dateCell = new Cell()
				.add(new Paragraph(sanitizePdfText(datesStr))
						.setFont(boldFont)
						.setFontSize(11)
						.setFontColor(ColorConstants.WHITE)
						.setTextAlignment(TextAlignment.RIGHT))
				.setBorder(Border.NO_BORDER)
				.setPadding(20)
				.setVerticalAlignment(VerticalAlignment.MIDDLE);

		headerTable.addCell(titleCell);
		headerTable.addCell(dateCell);
		document.add(headerTable);

		Paragraph accent = new Paragraph("")
				.setBackgroundColor(SECONDARY_COLOR)
				.setPaddingTop(3)
				.setMarginTop(0)
				.setMarginBottom(16);
		document.add(accent);
	}

	private void addTimetable(Document document, WeeklyPlanResponseDTO plan, PdfFont boldFont, PdfFont regularFont) {
		
		int maxDay = 5; // As per requirement: lunes a viernes
		if (plan.getSlots() != null) {
			for (WeeklyPlanSlotResponseDTO slot : plan.getSlots()) {
				if (slot.getDayOfWeek() != null && slot.getDayOfWeek() > maxDay) {
					maxDay = slot.getDayOfWeek();
				}
			}
		}

		float[] columnWidths = new float[maxDay];
		for (int i = 0; i < maxDay; i++) {
			columnWidths[i] = 1f;
		}

		Table table = new Table(UnitValue.createPercentArray(columnWidths))
				.setWidth(UnitValue.createPercentValue(100))
				.setMarginTop(8)
				.setMarginBottom(8);

		// Add headers (Días)
		for (int i = 1; i <= maxDay; i++) {
			String dayName = getDayName(i);
			table.addHeaderCell(createTableHeaderCell(dayName.toUpperCase(), boldFont));
		}

		// Add 1 single row, and inside each cell stack the slots
		Map<Integer, List<WeeklyPlanSlotResponseDTO>> slotsByDay = java.util.Collections.emptyMap();
		if (plan.getSlots() != null) {
			slotsByDay = plan.getSlots().stream()
				.collect(Collectors.groupingBy(slot -> slot.getDayOfWeek() == null ? 1 : slot.getDayOfWeek()));
		}

		for (int i = 1; i <= maxDay; i++) {
			Cell cell = new Cell()
				.setBorder(new SolidBorder(BORDER_COLOR, 1f))
				.setPadding(0);
				
			List<WeeklyPlanSlotResponseDTO> daySlots = slotsByDay.get(i);
			if (daySlots != null && !daySlots.isEmpty()) {
				daySlots.sort(Comparator.comparing(WeeklyPlanSlotResponseDTO::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
										.thenComparing(WeeklyPlanSlotResponseDTO::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())));
				
				for (int s = 0; s < daySlots.size(); s++) {
					WeeklyPlanSlotResponseDTO slot = daySlots.get(s);
					addSlotToCell(cell, slot, boldFont, regularFont);
					
					if (s < daySlots.size() - 1) {
						// Horizontal separator dashed
						Paragraph divider = new Paragraph()
							.setBorderBottom(new DashedBorder(BORDER_COLOR, 1f))
							.setMarginTop(0)
							.setMarginBottom(0)
							.setHeight(1);
						cell.add(divider);
					}
				}
			}
			table.addCell(cell);
		}

		document.add(table);
	}

	private void addSlotToCell(Cell cell, WeeklyPlanSlotResponseDTO slot, PdfFont boldFont, PdfFont regularFont) {
		
		// Hora
		String timeStr = "";
		if (slot.getStartTime() != null) {
			timeStr += slot.getStartTime().format(TIME_FORMAT);
			if (slot.getEndTime() != null) {
				timeStr += " - " + slot.getEndTime().format(TIME_FORMAT);
			}
		}
		
		if (!timeStr.isEmpty()) {
			Paragraph timeP = new Paragraph(timeStr)
				.setFont(boldFont)
				.setFontSize(9)
				.setFontColor(PRIMARY_COLOR)
				.setPaddingLeft(5).setPaddingRight(5).setPaddingTop(5).setMarginBottom(2);
			cell.add(timeP);
		}

		// Productos y cantidades
		String productLine = sanitizePdfText(slot.getRecipeName());
		if (slot.getQuantity() != null) {
			productLine += " (x" + formatDecimal(slot.getQuantity()) + ")";
		}
		
		Paragraph prodP = new Paragraph(productLine)
			.setFont(boldFont)
			.setFontSize(9)
			.setFontColor(TEXT_DARK)
			.setPaddingLeft(5).setPaddingRight(5).setPaddingTop(0).setMarginTop(0).setMarginBottom(2);
		cell.add(prodP);

		// Nombres de los alumnos
		if (slot.getStudents() != null && !slot.getStudents().isEmpty()) {
			Paragraph studentTitle = new Paragraph(i18nService.getMessage(MessageKey.REPORT_LABEL_STUDENTS))
				.setFont(boldFont)
				.setFontSize(7)
				.setFontColor(TEXT_GRAY)
				.setPaddingLeft(5).setPaddingRight(5).setPaddingTop(2).setMarginTop(0).setMarginBottom(0);
			cell.add(studentTitle);

			for (WeeklyPlanSlotStudentResponseDTO student : slot.getStudents()) {
				Paragraph studentP = new Paragraph("\u2022 " + sanitizePdfText(student.getStudentName()))
					.setFont(regularFont)
					.setFontSize(8)
					.setFontColor(TEXT_DARK)
					.setPaddingLeft(8).setPaddingRight(5).setMarginTop(0).setMarginBottom(0);
				cell.add(studentP);
			}
		}
		
		// Padding bottom to space out before the divider or next slot
		Paragraph spacer = new Paragraph("").setHeight(4).setMarginTop(0).setMarginBottom(0);
		cell.add(spacer);
	}

	private Cell createTableHeaderCell(String text, PdfFont boldFont) {
		return new Cell()
				.add(new Paragraph(text)
						.setFont(boldFont)
						.setFontSize(10)
						.setFontColor(TEXT_DARK)
						.setCharacterSpacing(0.5f)
						.setTextAlignment(TextAlignment.CENTER))
				.setBackgroundColor(TABLE_HEADER_BG)
				.setBorder(new SolidBorder(BORDER_COLOR, 1f))
				.setPadding(8)
				.setVerticalAlignment(VerticalAlignment.MIDDLE)
				.setTextAlignment(TextAlignment.CENTER);
	}

	private String getDayName(int dayOfWeek) {
		return switch (dayOfWeek) {
			case 1 -> i18nService.getMessage(MessageKey.REPORT_DAY_1);
			case 2 -> i18nService.getMessage(MessageKey.REPORT_DAY_2);
			case 3 -> i18nService.getMessage(MessageKey.REPORT_DAY_3);
			case 4 -> i18nService.getMessage(MessageKey.REPORT_DAY_4);
			case 5 -> i18nService.getMessage(MessageKey.REPORT_DAY_5);
			case 6 -> i18nService.getMessage(MessageKey.REPORT_DAY_6);
			case 7 -> i18nService.getMessage(MessageKey.REPORT_DAY_7);
			default -> "Día " + dayOfWeek;
		};
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
				PdfCanvas canvas = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), pdf);

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

	private String formatDecimal(BigDecimal value) {
		if (value == null) {
			return "0";
		}
		// Si tiene decimales ".00" no los muestra
		java.text.DecimalFormat df = new java.text.DecimalFormat("0.##");
		return df.format(value);
	}

	private String sanitizePdfText(String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("[^\\u0009\\u000A\\u000D\\u0020-\\u00FF]", "");
	}
}
