"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

const PAGE_SIZES = [10, 20, 50];

interface TaskPaginationProps {
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
}

export function TaskPagination({ page, size, totalPages, totalElements, onPageChange, onSizeChange }: TaskPaginationProps) {
  if (totalElements === 0) return null;

  const from = page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);

  return (
    <nav aria-label="Pagination" className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-sm text-muted-foreground">
        Showing <span className="font-medium text-foreground">{from}–{to}</span> of <span className="font-medium text-foreground">{totalElements}</span>
      </p>
      <div className="flex items-center gap-2">
        <Select value={String(size)} onValueChange={(value) => onSizeChange(Number(value))}>
          <SelectTrigger aria-label="Tasks per page" size="sm" className="w-[110px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {PAGE_SIZES.map((option) => (
              <SelectItem key={option} value={String(option)}>
                {option} / page
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button variant="outline" size="sm" onClick={() => onPageChange(page - 1)} disabled={page === 0} aria-label="Previous page">
          <ChevronLeft data-icon="inline-start" />
          Previous
        </Button>
        <span className="text-sm tabular-nums text-muted-foreground">
          Page {page + 1} of {Math.max(totalPages, 1)}
        </span>
        <Button variant="outline" size="sm" onClick={() => onPageChange(page + 1)} disabled={page + 1 >= totalPages} aria-label="Next page">
          Next
          <ChevronRight data-icon="inline-end" />
        </Button>
      </div>
    </nav>
  );
}
