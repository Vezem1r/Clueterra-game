const CONTACT_EMAIL = 'support@clueterra.com'
const OPERATOR_LOCATION = 'Ostrava, Czech Republic'
const TERMS_EFFECTIVE_DATE = 'September 3, 2026'
const PRIVACY_EFFECTIVE_DATE = 'September 13, 2026'

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="text-base sm:text-lg font-semibold text-white mb-2 sm:mb-3">{title}</h2>
      <div className="space-y-2">{children}</div>
    </section>
  )
}

function EmailLink() {
  return (
    <a href={`mailto:${CONTACT_EMAIL}`} className="text-accent hover:underline">
      {CONTACT_EMAIL}
    </a>
  )
}

export function PrivacyContent() {
  return (
    <>
      <p className="text-white/30 text-xs">Effective: {PRIVACY_EFFECTIVE_DATE}</p>

      <Section title="1. Controller and Contact">
        <p>
          Clueterra (&quot;we&quot;, &quot;us&quot;, &quot;our&quot;) is a web-based geography
          game operated by Ivan Akulovich, an individual based in {OPERATOR_LOCATION}. For the
          personal data described in this Privacy Policy, Clueterra acts as the data controller.
        </p>
        <p>
          Our electronic contact point for privacy requests, user support, authority
          communications, and content notices is <EmailLink />. We can receive communications in
          English and Czech.
        </p>
      </Section>

      <Section title="2. Personal Data We Process">
        <p>Depending on how you use the Service, we process the following categories of data:</p>
        <ul className="list-disc pl-5 space-y-1.5 mt-2">
          <li>
            <strong className="text-white/90">Account and profile data:</strong> email address,
            password hash for email/password accounts, username, display name, avatar, OAuth
            provider identifiers, role, account status, and legal document versions accepted.
          </li>
          <li>
            <strong className="text-white/90">OAuth data:</strong> if you use GitHub or Google
            sign-in, we receive limited profile data such as email address, name, avatar URL, and
            provider account ID. We do not request access to your repositories, contacts, or
            private files.
          </li>
          <li>
            <strong className="text-white/90">Gameplay data:</strong> sessions, guesses, hints
            revealed, scores, XP, levels, streaks, shields, badges, challenge history, and profile
            or leaderboard statistics.
          </li>
          <li>
            <strong className="text-white/90">Guest data:</strong> if you play without an account,
            we create a temporary guest profile linked to a random guest cookie so the Service can
            save guest gameplay progress, XP, streaks, shields, badges, legal acceptance versions,
            and guest session expiry.
          </li>
          <li>
            <strong className="text-white/90">Technical and security data:</strong> request
            metadata, timestamps, authentication/session identifiers, security events, derived
            rate-limiting signals, and device or browser metadata where needed to operate, secure,
            debug, or protect the Service. IP address data may be processed as necessary for
            network delivery, abuse prevention, fraud prevention, and rate limiting.
          </li>
          <li>
            <strong className="text-white/90">Local storage and cookies:</strong> authentication
            cookies, guest-session cookies/storage, OAuth state cookies, cookie notice state, and
            other strictly necessary storage used to provide requested features.
          </li>
        </ul>
        <p className="mt-3">
          We do <strong className="text-white/90">not</strong> intentionally collect payment card
          data, postal addresses, phone numbers, precise geolocation, or special-category personal
          data through the Service.
        </p>
      </Section>

      <Section title="3. Purposes and Legal Bases">
        <p>We process personal data for these purposes and legal bases:</p>
        <ul className="list-disc pl-5 space-y-1.5 mt-2">
          <li>
            <strong className="text-white/90">Providing accounts, authentication, guest sessions,
            and gameplay:</strong> performance of a contract or steps requested before entering a
            contract.
          </li>
          <li>
            <strong className="text-white/90">Saving XP, streaks, shields, badges, statistics, and
            guest progress:</strong> performance of the Service and, where applicable, our legitimate
            interest in providing consistent game progression.
          </li>
          <li>
            <strong className="text-white/90">Public profiles and leaderboards:</strong> our legitimate
            interest in operating expected social and competitive game features while limiting the
            displayed data to usernames, optional profile content, and gameplay information.
          </li>
          <li>
            <strong className="text-white/90">Security, rate limiting, abuse prevention, bug
            investigation, and anti-cheat:</strong> our legitimate interests in protecting users,
            the Service, and game integrity.
          </li>
          <li>
            <strong className="text-white/90">Legal acceptance records, legal claims, and
            compliance:</strong> legal obligation and legitimate interests in documenting acceptance
            or acknowledgement of current documents and defending rights.
          </li>
          <li>
            <strong className="text-white/90">Service communications:</strong> performance of the
            Service, legal obligation, or legitimate interests, depending on the message.
          </li>
          <li>
            <strong className="text-white/90">Optional marketing:</strong> consent, if we ever
            offer marketing emails. We do not send marketing emails unless you opt in.
          </li>
        </ul>
      </Section>

      <Section title="4. Public Features">
        <p>
          Your username, display name, avatar, badges, rank, and gameplay statistics may be shown
          publicly on profile pages, result screens, and leaderboards. Do not put private,
          sensitive, or third-party personal information in public profile content.
        </p>
        <p>
          Public profiles and rankings are normal features of a registered Clueterra account. You
          may object to processing based on legitimate interests by contacting us. We assess each
          objection and will restrict or stop the relevant processing where the GDPR requires it.
        </p>
      </Section>

      <Section title="5. Third-Party Services">
        <p>We disclose or make limited data available to the following recipients or categories:</p>
        <ul className="list-disc pl-5 space-y-1.5 mt-2">
          <li>GitHub or Google only when you choose the corresponding third-party sign-in option.</li>
          <li>
            Cloudflare for DNS, traffic delivery, network security, email routing, and encrypted
            off-site backups stored in a private R2 bucket.
          </li>
          <li>
            Google consumer Gmail, used as the final mailbox for messages sent to our contact
            address after Cloudflare Email Routing.
          </li>
          <li>
            Public data or asset providers used for game content. When an asset is loaded directly
            from a third-party host, your browser contacts that host and may disclose your IP
            address, browser or device headers, and limited referrer information.
          </li>
          <li>Authorities, professional advisers, or affected parties where required by law or needed to protect rights and safety.</li>
        </ul>
        <p className="mt-3">
          Some listed providers operate globally and may process data outside the EEA. Where
          Chapter V of the GDPR requires a transfer safeguard, we rely on an applicable adequacy
          decision, Data Privacy Framework participation, Standard Contractual Clauses in the
          provider&apos;s data processing terms, or another valid transfer mechanism, according to
          the provider&apos;s role and service.
        </p>
      </Section>

      <Section title="6. Game Content and AI-Assisted Tools">
        <p>
          We use public datasets, source registries, and AI-assisted tools to help create and
          review geography hints. This processing is focused on game content and public facts, not
          on your account email, password, or private profile information.
        </p>
        <p>
          Game hints and image references can come from public or third-party sources and may be
          inaccurate, outdated, or unsuitable by mistake. We may filter, replace, disable, or remove
          content to protect users, minors, rights holders, and the integrity of the Service.
        </p>
      </Section>

      <Section title="7. Cookies and Local Storage">
        <p>
          We use cookies and local storage that are necessary to provide the Service, including
          authentication, guest play, OAuth security, legal acceptance, and basic UI state. We do
          not use advertising cookies or third-party analytics tools such as Google Analytics.
        </p>
        <p>
          Authentication cookies last for up to one day for access and seven days for refresh;
          guest cookies last for up to 30 days; and OAuth state cookies last for up to 10 minutes.
          Cookie-notice and interface preferences remain in browser storage until you clear them or
          the stored version is replaced. Cloudflare may set strictly necessary security cookies,
          such as a challenge-clearance cookie, according to the active security configuration.
        </p>
      </Section>

      <Section title="8. Retention">
        <p>
          Account, profile, legal-acceptance, and identifiable gameplay data are retained while your
          account is active. Guest profiles and guest progress normally expire after 30 days unless
          you convert the guest profile into a registered account before expiry.
        </p>
        <p>
          Local application logs and centralized operational logs are normally retained for up to
          30 days. Security records may be retained longer only where a
          documented incident, abuse investigation, or legal claim requires it. Ordinary support
          correspondence is kept until the request is resolved and then only while follow-up,
          security, or legal-claim needs remain.
        </p>
        <p>
          Production backups are encrypted before upload to a private Cloudflare R2 bucket and
          expire after 30 days; no more than three encrypted copies are kept locally. The decryption
          key is stored separately from the hosting environment. Backups are used only for disaster
          recovery. If a backup is restored, completed deletion requests are reapplied before normal
          operation resumes.
        </p>
        <p>
          If you request account deletion, we generally aim to delete or anonymize personal data
          associated with your account within 30 days, unless limited retention is needed for legal
          compliance, security, fraud prevention, dispute resolution, or legitimate record-keeping.
          Active database and avatar-storage records are covered by a documented deletion procedure;
          encrypted backup copies expire on the schedule above. Aggregated or anonymized statistics
          may be retained longer.
        </p>
      </Section>

      <Section title="9. Your GDPR Rights">
        <p>
          If you are in the EEA, UK, or another jurisdiction with similar rights, you may have the
          right to access, rectify, erase, restrict, export, or object to processing of your
          personal data, and to withdraw consent where processing is based on consent.
        </p>
        <p>
          To exercise your rights, contact us at <EmailLink />. We normally respond within one
          month, subject to proportionate identity verification and lawful extensions. Requests are
          handled through a documented manual procedure covering access, correction, deletion,
          avatar storage, and verification of the result.
        </p>
        <p>
          You may also lodge a complaint with a supervisory authority. In the Czech Republic, the
          supervisory authority is the Czech Office for Personal Data Protection (UOOU).
        </p>
      </Section>

      <Section title="10. Children and Minors">
        <p>
          Clueterra is not directed to children under 15 in the Czech Republic. If you are under 15,
          do not create an account or use guest features that require legal acceptance unless your
          parent or guardian allows and supervises your use. We do not knowingly collect personal
          data from children who are not legally permitted to use the Service. If we learn that such
          data was provided, we will take reasonable steps to delete it.
        </p>
      </Section>

      <Section title="11. Security">
        <p>
          We use technical and organizational measures designed to protect personal data, including
          password hashing, HTTPS, restricted database access, authentication controls, rate
          limiting, encrypted off-site backups, and separation of backup decryption keys from the
          hosting environment. No online service can guarantee absolute security.
        </p>
      </Section>

      <Section title="12. Changes">
        <p>
          We may update this Privacy Policy from time to time. Material changes will be reflected
          on this page with an updated effective date. Where appropriate, we may require users to
          review and accept updated legal documents before continuing certain account or guest
          features.
        </p>
      </Section>

      <Section title="13. Contact">
        <p>
          For privacy requests, account deletion, content notices, or questions about this Privacy
          Policy, contact us at <EmailLink />.
        </p>
      </Section>
    </>
  )
}

export function TermsContent() {
  return (
    <>
      <p className="text-white/30 text-xs">Effective: {TERMS_EFFECTIVE_DATE}</p>

      <Section title="1. Acceptance">
        <p>
          By creating an account, starting a guest session, or otherwise using Clueterra
          (&quot;Service&quot;), you agree to these Terms of Service (&quot;Terms&quot;). If you do
          not agree, do not use the Service.
        </p>
      </Section>

      <Section title="2. Operator and Contact">
        <p>
          Clueterra is operated by Ivan Akulovich, an individual based in {OPERATOR_LOCATION}. Our
          electronic contact point for user support, authority communications, content notices, and
          legal questions is <EmailLink />. We can receive communications in English and Czech.
        </p>
      </Section>

      <Section title="3. Eligibility">
        <p>
          Clueterra is not intended for children under 15 in the Czech Republic. If you are under
          15, do not create an account or start guest features requiring legal acceptance unless
          your parent or guardian allows and supervises your use. If the law where you live
          requires a higher minimum age or additional consent, that rule applies.
        </p>
      </Section>

      <Section title="4. The Service">
        <p>
          Clueterra is a free web-based geography guessing game. The Service includes daily
          challenges, guest play, registered accounts, public profiles, leaderboards, XP, streaks,
          shields, badges, hints, and related game content. We may add, remove, suspend, or change
          features at any time.
        </p>
      </Section>

      <Section title="5. Accounts, Guest Access, and Public Features">
        <p>
          You may register using email/password or a supported third-party sign-in provider such as
          GitHub or Google. You are responsible for your account credentials and for activity under
          your account.
        </p>
        <p>
          You may also play as a guest. Guest progress is temporary, linked to a guest cookie, and
          may expire or be deleted unless you convert it into a registered account before expiry.
        </p>
        <p>
          Usernames, display names, avatars, badges, ranks, scores, streaks, and other gameplay
          statistics may appear on public profiles, result screens, and leaderboards.
        </p>
      </Section>

      <Section title="6. Game Progress and Rewards">
        <p>
          XP, levels, streaks, shields, badges, scores, and similar game rewards are part of the
          Service. They have no monetary value, are not property, are not transferable, and cannot
          be sold, exchanged, refunded, or redeemed for money or anything of value.
        </p>
        <p>
          We may correct, adjust, remove, or reset game progress or rewards if we reasonably
          believe they resulted from bugs, cheating, automation, abuse, data corruption, security
          incidents, or behavior that harms the Service or other users.
        </p>
      </Section>

      <Section title="7. Acceptable Use and Content Rules">
        <p>You agree not to:</p>
        <ul className="list-disc pl-5 space-y-1.5 mt-2">
          <li>Use the Service for unlawful, harmful, abusive, or fraudulent purposes.</li>
          <li>Cheat, automate gameplay, scrape the Service, exploit bugs, or manipulate rankings.</li>
          <li>Attempt unauthorized access to accounts, systems, APIs, or data.</li>
          <li>Create multiple accounts to evade restrictions, manipulate leaderboards, or abuse guest play.</li>
          <li>Impersonate another person or misrepresent your identity.</li>
          <li>
            Upload, submit, or display content that is illegal, pornographic, sexually explicit,
            hateful, harassing, threatening, violent, exploitative, infringing, privacy-invasive,
            or otherwise unsuitable for a general geography game.
          </li>
          <li>Use usernames, avatars, or profile content that contain sensitive personal data about another person without permission.</li>
        </ul>
      </Section>

      <Section title="8. Reports, Moderation, and Appeals">
        <p>
          If you believe that content in the Service is illegal, infringes rights, is inappropriate
          for minors, or violates these Terms, contact us at <EmailLink /> with enough information
          to identify the content and explain the issue. For public profiles, usernames, or
          avatars, include the profile URL or username so we can review the reported content.
        </p>
        <p>
          We may review reports manually or with automated assistance. We may remove, disable,
          replace, restrict, or refuse content or accounts where reasonably necessary to enforce
          these Terms, comply with law, protect minors, protect rights holders, prevent abuse, or
          preserve game integrity.
        </p>
        <p>
          If your content or account is restricted and you believe this was a mistake, you may ask
          us to review the decision by contacting <EmailLink />.
        </p>
      </Section>

      <Section title="9. User Content License">
        <p>
          You retain rights in content you submit or upload, such as profile avatars. You grant us
          a non-exclusive, worldwide, royalty-free license to host, store, process, adapt, resize,
          display, and remove that content as needed to operate, secure, moderate, and improve the
          Service.
        </p>
        <p>
          You must have the rights needed to upload or display your content. Do not upload content
          that infringes copyright, trademarks, privacy rights, publicity rights, or other rights.
        </p>
      </Section>

      <Section title="10. Third-Party Data and Game Content">
        <p>
          Clueterra uses public datasets, third-party resources, and AI-assisted tools to generate
          and review hints, images, and country-related game content. The data source registry is
          provided for attribution and transparency; it does not mean that any third-party provider
          sponsors, endorses, or is responsible for Clueterra.
        </p>
        <p>
          Game content may contain mistakes, outdated facts, missing context, or unsuitable
          material by mistake. We may filter, correct, replace, remove, or disable content at any
          time.
        </p>
      </Section>

      <Section title="11. Suspension and Termination">
        <p>
          We may suspend, restrict, or terminate accounts, guest sessions, content, or access to
          the Service where we reasonably believe this is necessary for security, legal compliance,
          abuse prevention, anti-cheat enforcement, moderation, or protection of users and the
          Service. Serious risks may require immediate action.
        </p>
        <p>You may request account deletion by contacting <EmailLink />.</p>
      </Section>

      <Section title="12. Availability">
        <p>
          We do not guarantee uninterrupted availability. The Service may be unavailable due to
          maintenance, updates, infrastructure issues, security events, provider outages, or events
          outside our control.
        </p>
      </Section>

      <Section title="13. Disclaimers">
        <p>
          The Service is provided &quot;as is&quot; and &quot;as available&quot;. To the maximum extent
          permitted by law, we disclaim all warranties, including implied warranties of
          merchantability, fitness for a particular purpose, non-infringement, and uninterrupted or
          error-free operation.
        </p>
        <p>
          The Service is a game. Hints, rankings, statistics, and public data are provided for
          entertainment and informational gameplay only and should not be relied on as professional,
          legal, travel, safety, financial, or other advice.
        </p>
      </Section>

      <Section title="14. Limitation of Liability">
        <p>
          To the maximum extent permitted by applicable law, Clueterra and its operator will not be
          liable for indirect, incidental, special, consequential, punitive, or similar damages, or
          for loss of data, scores, progress, rewards, reputation, availability, or access arising
          from or related to the Service.
        </p>
        <p>
          To the maximum extent permitted by law, our total aggregate liability arising out of or
          relating to the Service will not exceed the greater of EUR 100 or the amount you paid us
          for the Service in the 12 months before the event giving rise to the claim.
        </p>
        <p>
          Nothing in these Terms excludes or limits liability that cannot be excluded or limited
          under applicable law, including mandatory consumer rights.
        </p>
      </Section>

      <Section title="15. Changes to These Terms">
        <p>
          We may update these Terms from time to time. Material changes will be posted on this page
          with an updated effective date. Where appropriate, we may require users to review and
          accept updated Terms before continuing certain account or guest features.
        </p>
      </Section>

      <Section title="16. Governing Law">
        <p>
          To the extent permitted by applicable law, these Terms are governed by the laws of the
          Czech Republic. If you are a consumer, nothing in these Terms limits mandatory consumer
          rights or protections that apply in your country of residence.
        </p>
      </Section>
    </>
  )
}
