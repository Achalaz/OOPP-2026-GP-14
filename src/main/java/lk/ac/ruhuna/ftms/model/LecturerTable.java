package lk.ac.ruhuna.ftms.model;
import java.util.List;
/** Explicit, read-only projection; never includes credentials or medical documents. */
public record LecturerTable(List<String> columns, List<List<String>> rows) {}
