import { z } from "zod";
import { TASK_PRIORITIES, TASK_STATUSES } from "@/types/task";

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Mirrors the Jakarta Bean Validation rules on the backend DTOs. */
export const taskFormSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, "Title is required")
    .max(150, "Title must be at most 150 characters"),
  description: z.string().trim().max(2000, "Description must be at most 2000 characters"),
  priority: z.enum(TASK_PRIORITIES, { message: "Choose a priority" }),
  status: z.enum(TASK_STATUSES, { message: "Choose a status" }),
  dueDate: z
    .string()
    .refine((value) => value === "" || ISO_DATE.test(value), "Enter a valid date"),
});

export type TaskFormValues = z.infer<typeof taskFormSchema>;

export const defaultTaskFormValues: TaskFormValues = {
  title: "",
  description: "",
  priority: "MEDIUM",
  status: "TODO",
  dueDate: "",
};
