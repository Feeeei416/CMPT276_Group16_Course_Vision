package cmpt276.group16.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Season;
import cmpt276.group16.models.Term;
import cmpt276.group16.models.TermRepository;

// Saves a new row in terms in its own transaction. If two students add the same new term at the same moment,
// the second save breaks the unique (term_year, season) rule. In its own transaction that failure can be caught
// and the term looked up again; inside the plan change's transaction it would fail the whole change
@Service
public class TermCreator {

    private final TermRepository termRepository;

    public TermCreator(TermRepository termRepository) {
        this.termRepository = termRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Term create(int year, Season season) {
        return termRepository.saveAndFlush(new Term(year, season));
    }
}
