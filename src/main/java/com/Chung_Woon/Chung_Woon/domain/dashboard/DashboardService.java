package com.Chung_Woon.Chung_Woon.domain.dashboard;

import com.Chung_Woon.Chung_Woon.ai.AiClient;
import com.Chung_Woon.Chung_Woon.api.dto.DashboardResponse;
import com.Chung_Woon.Chung_Woon.domain.instruction.ConstraintStatus;
import com.Chung_Woon.Chung_Woon.domain.instruction.Instruction;
import com.Chung_Woon.Chung_Woon.domain.instruction.InstructionRepository;
import com.Chung_Woon.Chung_Woon.domain.instruction.PlanConstraint;
import com.Chung_Woon.Chung_Woon.domain.instruction.PlanConstraintRepository;
import com.Chung_Woon.Chung_Woon.domain.plan.MoveTask;
import com.Chung_Woon.Chung_Woon.domain.plan.MoveTaskRepository;
import com.Chung_Woon.Chung_Woon.domain.plan.PlanRevision;
import com.Chung_Woon.Chung_Woon.domain.plan.PlanRevisionRepository;
import com.Chung_Woon.Chung_Woon.domain.plan.PlanStatus;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleRepository;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleStatus;
import com.Chung_Woon.Chung_Woon.domain.yard.Block;
import com.Chung_Woon.Chung_Woon.domain.yard.BlockLayout;
import com.Chung_Woon.Chung_Woon.domain.yard.BlockRepository;
import com.Chung_Woon.Chung_Woon.domain.yard.SlotRepository;
import com.Chung_Woon.Chung_Woon.domain.yard.SlotStatus;
import com.Chung_Woon.Chung_Woon.domain.yard.YardGrid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관제 콘솔 대시보드 한 화면을 채운다.
 *
 * <p>화면 하나에 필요한 걸 요청 하나로 준다 — 카드 4개, 블록 4개, 활동 피드, 최근 지시.
 * 프론트가 여러 API 를 조합하지 않아도 되게 하는 게 목적이다(FRONTEND_CONTRACT.md).
 *
 * <p>전부 카운트 쿼리다. 슬롯이 1,936행이라 목록을 통째로 들고오면 안 된다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

	/** 이 비율을 넘으면 "혼잡". */
	private static final double CONGESTED_THRESHOLD = 0.90;
	private static final double BUSY_THRESHOLD = 0.75;

	private static final int RECENT_LIMIT = 5;
	private static final int SUMMARY_MAX_LENGTH = 40;

	private final VehicleRepository vehicleRepository;
	private final SlotRepository slotRepository;
	private final BlockRepository blockRepository;
	private final PlanConstraintRepository constraintRepository;
	private final InstructionRepository instructionRepository;
	private final PlanRevisionRepository planRevisionRepository;
	private final MoveTaskRepository moveTaskRepository;
	private final AiClient aiClient;

	@Transactional(readOnly = true)
	public DashboardResponse load() {
		List<Block> blocks = blockRepository.findAll();
		Map<String, Long> occupiedByBlock = occupiedCountByBlock();
		PlanRevision latestPlan = latestPlan();

		return new DashboardResponse(
				LocalDateTime.now(),
				buildSummary(latestPlan),
				buildBlocks(blocks, occupiedByBlock, latestPlan),
				buildActivities(),
				buildRecentInstructions(),
				buildAiEngine());
	}

	/**
	 * 화면에 쓸 기준 판. <b>승인된 판</b>만 본다 — DRAFT 는 아직 현장에 반영되지 않았으므로
	 * 대시보드 숫자로 내보내면 안 된다(docs/DOMAIN.md 설계 원칙 1).
	 */
	private PlanRevision latestPlan() {
		return planRevisionRepository
				.findFirstByStatusOrderByApprovedAtDesc(PlanStatus.APPROVED)
				.orElse(null);
	}

	// ------------------------------------------------------------------ 카드

	private DashboardResponse.Summary buildSummary(PlanRevision latestPlan) {
		long inYard = vehicleRepository.countByStatus(VehicleStatus.IN_YARD);
		long occupied = slotRepository.countByStatus(SlotStatus.OCCUPIED);
		// 총 칸 수는 코드 상수다. DB 에 격자가 아직 안 깔렸다고 "0 칸" 이라고 말하면
		// 화면에 "0 / 0" 이 떠서 야드가 없는 것처럼 보인다.
		long total = YardGrid.SLOT_COUNT;
		long available = slotRepository.count() == 0 ? total : slotRepository.countAssignable();

		double occupancyPct = total == 0 ? 0.0 : round1(occupied * 100.0 / total);
		String congestion = congestionOf(total == 0 ? 0.0 : (double) occupied / total);

		Double avgDistance = latestPlan != null && latestPlan.getKpi() != null
				? latestPlan.getKpi().getAvgMoveDistance()
				: null;

		return new DashboardResponse.Summary(
				inYard,
				arrivedToday(),
				constraintRepository.countByStatus(ConstraintStatus.PENDING_REVIEW),
				available,
				total,
				occupancyPct,
				congestion,
				congestionLabel(congestion),
				avgDistance,
				avgMoveDistanceDeltaPct(latestPlan));
	}

	/** 오늘 0시 이후 야드에 들어온 대수. */
	private long arrivedToday() {
		LocalDateTime midnight = LocalDate.now().atStartOfDay();
		return vehicleRepository.findByStatus(VehicleStatus.IN_YARD).stream()
				.filter(v -> v.getArrivedAt() != null && v.getArrivedAt().isAfter(midnight))
				.count();
	}

	/**
	 * 직전 판 대비 평균 이동거리 증감률. 비교할 판이 없으면 null 이다 —
	 * 화면에서 "-12%" 같은 배지를 못 그리는 게, 없는 숫자를 지어내는 것보다 낫다.
	 */
	private Double avgMoveDistanceDeltaPct(PlanRevision latestPlan) {
		if (latestPlan == null || latestPlan.getKpi() == null || latestPlan.getBasedOnVersion() == null) {
			return null;
		}
		Double current = latestPlan.getKpi().getAvgMoveDistance();
		Double previous = planRevisionRepository.findByPlanVersion(latestPlan.getBasedOnVersion())
				.map(PlanRevision::getKpi)
				.map(kpi -> kpi.getAvgMoveDistance())
				.orElse(null);
		if (current == null || previous == null || previous == 0.0) {
			return null;
		}
		return round1((current - previous) / previous * 100.0);
	}

	private String congestionOf(double ratio) {
		if (ratio >= CONGESTED_THRESHOLD) {
			return "CONGESTED";
		}
		return ratio >= BUSY_THRESHOLD ? "BUSY" : "NORMAL";
	}

	private String congestionLabel(String congestion) {
		return switch (congestion) {
			case "CONGESTED" -> "혼잡";
			case "BUSY" -> "보통";
			default -> "여유";
		};
	}

	// ------------------------------------------------------------------ 블록

	private Map<String, Long> occupiedCountByBlock() {
		Map<String, Long> counts = new HashMap<>();
		for (Object[] row : slotRepository.countByStatusGroupedByBlock(SlotStatus.OCCUPIED)) {
			counts.put((String) row[0], (Long) row[1]);
		}
		return counts;
	}

	/**
	 * 블록 카드 4개. <b>구조는 코드 정의(BlockLayout)가 정본이고</b> 폐쇄 여부만 DB 에서 온다.
	 *
	 * <p>DB 만 읽으면 격자가 적재되기 전에 카드가 하나도 안 그려진다("연동된 블록 데이터가 없습니다").
	 * 블록이 넷이라는 건 항상 아는 값이라 DB 를 기다릴 이유가 없다.
	 */
	private List<DashboardResponse.BlockStatus> buildBlocks(
			List<Block> blocks, Map<String, Long> occupiedByBlock, PlanRevision latestPlan) {

		Map<String, Long> movesByBlock = pendingMoveCountByBlock(latestPlan);
		Map<String, Block> storedById = new HashMap<>();
		blocks.forEach(b -> storedById.put(b.getBlockId(), b));

		List<DashboardResponse.BlockStatus> result = new ArrayList<>(YardGrid.BLOCK_COUNT);
		for (BlockLayout layout : BlockLayout.values()) {
			Block stored = storedById.get(layout.blockId());
			long occupied = occupiedByBlock.getOrDefault(layout.blockId(), 0L);
			long capacity = YardGrid.SLOTS_PER_BLOCK;
			long movingIn = movesByBlock.getOrDefault(layout.blockId(), 0L);
			boolean closed = stored != null && stored.isClosed();

			String status;
			String note;
			if (closed) {
				status = "CLOSED";
				note = stored.getClosureReason() != null
						? stored.getClosureReason() + "으로 폐쇄"
						: "폐쇄됨";
			} else if (movingIn > 0) {
				status = "RELOCATING";
				note = "%d대 이동 진행".formatted(movingIn);
			} else {
				status = "OPERATING";
				note = "변동 없음";
			}

			result.add(new DashboardResponse.BlockStatus(
					layout.blockId(),
					stored != null ? stored.getZoneCode() : layout.zoneCode(),
					status, blockStatusLabel(status), occupied, capacity, note));
		}
		return result;
	}

	/** 최신 판에서 아직 안 끝난 이동 작업을 도착 블록 기준으로 센다. */
	private Map<String, Long> pendingMoveCountByBlock(PlanRevision latestPlan) {
		if (latestPlan == null) {
			return Map.of();
		}
		Map<String, Long> counts = new HashMap<>();
		for (MoveTask task : moveTaskRepository
				.findByPlanRevision_PlanVersionOrderBySequenceAsc(latestPlan.getPlanVersion())) {
			if (task.getToSlot() == null || task.getToSlot().getBlock() == null) {
				continue;
			}
			counts.merge(task.getToSlot().getBlock().getBlockId(), 1L, Long::sum);
		}
		return counts;
	}

	private String blockStatusLabel(String status) {
		return switch (status) {
			case "CLOSED" -> "폐쇄됨";
			case "RELOCATING" -> "재배치 중";
			default -> "운영 중";
		};
	}

	// ------------------------------------------------------------------ 활동 · 지시

	/** 제약 심사와 지시 접수를 시간순으로 섞는다. */
	private List<DashboardResponse.Activity> buildActivities() {
		List<DashboardResponse.Activity> activities = new ArrayList<>();

		for (PlanConstraint c : constraintRepository.findTop10ByOrderByCreatedAtDesc()) {
			if (c.getStatus() == ConstraintStatus.APPROVED && c.getReviewedAt() != null) {
				activities.add(new DashboardResponse.Activity(
						"CONSTRAINT_APPROVED", "제약 승인됨",
						"%s %s 제약이 승인되었습니다.".formatted(c.getConstraintId(), typeLabel(c)),
						c.getReviewedAt()));
			} else if (c.getStatus() == ConstraintStatus.REJECTED && c.getReviewedAt() != null) {
				activities.add(new DashboardResponse.Activity(
						"CONSTRAINT_REJECTED", "제약 반려됨",
						"%s 제약이 반려되었습니다. (%s)".formatted(
								c.getConstraintId(),
								c.getRejectionReason() != null ? c.getRejectionReason() : "사유 없음"),
						c.getReviewedAt()));
			}
		}

		for (Instruction i : instructionRepository.findTop10ByOrderByCreatedAtDesc()) {
			activities.add(new DashboardResponse.Activity(
					"INSTRUCTION_CREATED", "지시 접수",
					"새 지시가 입력되었습니다 (%s).".formatted(i.getInstructionId()),
					i.getCreatedAt()));
		}

		return activities.stream()
				.sorted(Comparator.comparing(DashboardResponse.Activity::occurredAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.limit(RECENT_LIMIT)
				.toList();
	}

	private String typeLabel(PlanConstraint c) {
		return switch (c.getType()) {
			case BLOCK_CLOSURE -> "블록 폐쇄";
			case VEHICLE_GROUPING -> "차량 그룹핑";
			case OUTBOUND_PRIORITY -> "출고 우선";
		};
	}

	private List<DashboardResponse.RecentInstruction> buildRecentInstructions() {
		return instructionRepository.findTop10ByOrderByCreatedAtDesc().stream()
				.limit(RECENT_LIMIT)
				.map(i -> {
					String status = instructionStatus(i);
					return new DashboardResponse.RecentInstruction(
							i.getInstructionId(), i.getAuthor(), summarize(i.getRawText()),
							status, instructionStatusLabel(status), i.getCreatedAt());
				})
				.toList();
	}

	/**
	 * 지시의 상태는 딸린 제약들에서 유도한다. 하나라도 승인 대기면 대기,
	 * 전부 반려면 반려, 하나라도 승인이면 승인이다.
	 */
	private String instructionStatus(Instruction instruction) {
		List<PlanConstraint> constraints =
				constraintRepository.findByInstruction_InstructionId(instruction.getInstructionId());
		if (constraints.isEmpty()) {
			return "NOT_PARSED";
		}
		if (constraints.stream().anyMatch(c -> c.getStatus() == ConstraintStatus.PENDING_REVIEW)) {
			return "PENDING_REVIEW";
		}
		if (constraints.stream().anyMatch(c -> c.getStatus() == ConstraintStatus.APPROVED)) {
			return "APPROVED";
		}
		return "REJECTED";
	}

	private String instructionStatusLabel(String status) {
		return switch (status) {
			case "APPROVED" -> "승인됨";
			case "PENDING_REVIEW" -> "승인 대기";
			case "REJECTED" -> "반려됨";
			default -> "파싱 전";
		};
	}

	/** 지시 원문을 테이블 한 줄에 들어가게 줄인다. */
	private String summarize(String rawText) {
		if (rawText == null) {
			return "";
		}
		String oneLine = rawText.replaceAll("\\s+", " ").strip();
		return oneLine.length() <= SUMMARY_MAX_LENGTH
				? oneLine
				: oneLine.substring(0, SUMMARY_MAX_LENGTH) + "…";
	}

	// ------------------------------------------------------------------ AI

	/**
	 * AI 서비스 상태. 죽어 있어도 대시보드는 떠야 하므로 실패를 그대로 삼킨다 —
	 * "파이썬이 죽어 있어도 조회 API 는 살아 있어야 한다"(API_CONTRACT.md).
	 */
	private DashboardResponse.AiEngine buildAiEngine() {
		AiClient.HealthSnapshot health = aiClient.health();
		if (!health.up()) {
			return new DashboardResponse.AiEngine("DOWN", "응답 없음", null);
		}
		boolean gemini = Boolean.TRUE.equals(health.geminiEnabled());
		return gemini
				? new DashboardResponse.AiEngine("UP", "정상", true)
				// 키가 없으면 서버는 200 을 주지만 고정 더미를 돌려준다. 정상이라고 표시하면 안 된다.
				: new DashboardResponse.AiEngine("DEGRADED", "폴백 동작 중", false);
	}

	private static double round1(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}
