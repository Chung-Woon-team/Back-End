package com.Chung_Woon.Chung_Woon.domain.instruction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstructionRepository extends JpaRepository<Instruction, String> {

	List<Instruction> findByRequiresConfirmationTrue();
}
