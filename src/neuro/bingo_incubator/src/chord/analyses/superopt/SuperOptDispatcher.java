package chord.analyses.superopt;

import java.util.ArrayList;

import chord.project.analyses.parallelizer.JobDispatcher;
import chord.project.analyses.parallelizer.Scenario;

public class SuperOptDispatcher implements JobDispatcher {
	
	private static int cnt = 0;
	private ArrayList<ArrayList<String>> jobs;

	
	public SuperOptDispatcher (ArrayList<ArrayList<String>> j) {
		jobs = j;
	}
	
	@Override
	public Scenario createJob() {
		StringBuilder sb = new StringBuilder();
		String inp;
		System.out.println("Creating Job: " + cnt);
		if (cnt < jobs.size()) {
			ArrayList<String> job = jobs.get(cnt);
			System.out.println("Job size: " + job.size());
			for (int i = 0; i < job.size(); i++) {
				sb.append(job.get(i));
				if (i - 1 < job.size()) sb.append(",");
			}
			inp = sb.toString();
		} else
			inp = "DUMMY";
		Scenario s = new Scenario("METH", inp, "DUMMY", " ");
		cnt ++;
		return s;
	}

	@Override
	public boolean isDone() {
		if (cnt >= jobs.size())
			return true;
		else
			return false;
	}

	@Override
	public int maxWorkersNeeded() {
		return jobs.size();
	}

	@Override
	public void onError(int arg0) {

	}

	@Override
	public void onJobResult(Scenario arg0) {

	}

	@Override
	public void saveState() {

	}
}
